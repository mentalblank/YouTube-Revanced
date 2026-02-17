package app.revanced.integrations.youtube.vot;

import app.revanced.integrations.youtube.vot.api.YandexTranslationClient;
import app.revanced.integrations.youtube.vot.api.YandexTranslationClient.TranslationResult;
import app.revanced.integrations.youtube.vot.player.AudioDuckingManager;
import app.revanced.integrations.youtube.vot.player.AudioSyncController;
import app.revanced.integrations.youtube.vot.player.TranslationAudioManager;

/**
 * Coordinates the full VOT translation flow:
 * request translation → poll → load audio → sync → duck.
 *
 * State machine:
 *   IDLE → REQUESTING → LOADING → PLAYING
 *   Any state → ERROR (on failure)
 *   Any state → IDLE (on stop/video change)
 *
 * Thread safety: startTranslation runs the API request on a background thread
 * (via the provided Executor), while state transitions are synchronized.
 */
public class VotTranslationCoordinator {

    public enum State {
        IDLE,
        REQUESTING,
        LOADING,
        PLAYING,
        ERROR
    }

    /** Listener for coordinator state changes. */
    public interface StateListener {
        void onStateChanged(State oldState, State newState);
        void onError(String message);
    }

    /** Abstraction for running work off the main thread. */
    public interface Executor {
        void execute(Runnable task);
    }

    /** Abstraction for posting back to the main/UI thread. */
    public interface MainThreadPoster {
        void post(Runnable task);
    }

    private final YandexTranslationClient translationClient;
    private final TranslationAudioManager audioManager;
    private final AudioDuckingManager duckingManager;
    private final AudioSyncController syncController;
    private final Executor executor;
    private final MainThreadPoster mainThread;

    private State state = State.IDLE;
    private StateListener listener;
    private String currentVideoId;
    private volatile boolean cancelled = false;

    public VotTranslationCoordinator(
            YandexTranslationClient translationClient,
            TranslationAudioManager audioManager,
            AudioDuckingManager duckingManager,
            AudioSyncController syncController,
            Executor executor,
            MainThreadPoster mainThread) {
        this.translationClient = translationClient;
        this.audioManager = audioManager;
        this.duckingManager = duckingManager;
        this.syncController = syncController;
        this.executor = executor;
        this.mainThread = mainThread;
    }

    public void setStateListener(StateListener listener) {
        this.listener = listener;
    }

    public synchronized State getState() {
        return state;
    }

    public synchronized String getCurrentVideoId() {
        return currentVideoId;
    }

    /**
     * Start translation for a video. If a translation is already active,
     * stops it first (handles video change).
     *
     * @param videoId YouTube video ID
     * @param targetLanguage target language code (e.g. "ru")
     */
    public void startTranslation(String videoId, String targetLanguage) {
        if (videoId == null || videoId.isEmpty()) {
            throw new IllegalArgumentException("videoId must not be null or empty");
        }
        if (targetLanguage == null || targetLanguage.isEmpty()) {
            throw new IllegalArgumentException("targetLanguage must not be null or empty");
        }

        synchronized (this) {
            // If already translating a different video, stop first
            if (state != State.IDLE) {
                doStop();
            }
            currentVideoId = videoId;
            cancelled = false;
            transitionTo(State.REQUESTING);
        }

        // Run API request on background thread
        executor.execute(() -> {
            try {
                String videoUrl = "https://www.youtube.com/watch?v=" + videoId;
                TranslationResult result = translationClient.requestTranslation(
                        videoUrl, "en", targetLanguage, 0);

                synchronized (this) {
                    if (cancelled || !videoId.equals(currentVideoId)) {
                        return; // Video changed during request
                    }
                }

                if (result.isSuccess()) {
                    onTranslationReady(videoId, result.audioUrl);
                } else {
                    onTranslationError("Translation failed: " + result.message);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                onTranslationError("Translation interrupted");
            } catch (Exception e) {
                onTranslationError("Translation error: " + e.getMessage());
            }
        });
    }

    private void onTranslationReady(String videoId, String audioUrl) {
        mainThread.post(() -> {
            synchronized (this) {
                if (cancelled || !videoId.equals(currentVideoId)) {
                    return;
                }
                transitionTo(State.LOADING);
            }

            try {
                audioManager.loadAudio(audioUrl);
                audioManager.play();

                synchronized (this) {
                    if (cancelled || !videoId.equals(currentVideoId)) {
                        audioManager.stop();
                        return;
                    }
                    transitionTo(State.PLAYING);
                }

                duckingManager.startDucking();
                syncController.start();
            } catch (Exception e) {
                onTranslationError("Failed to load audio: " + e.getMessage());
            }
        });
    }

    private void onTranslationError(String message) {
        mainThread.post(() -> {
            synchronized (this) {
                if (state == State.IDLE) return; // Already stopped
                transitionTo(State.ERROR);
            }
            if (listener != null) {
                listener.onError(message);
            }
        });
    }

    /**
     * Stop current translation and restore original audio volume.
     */
    public void stopTranslation() {
        synchronized (this) {
            if (state == State.IDLE) return;
            doStop();
        }
    }

    /**
     * Called when the video changes. Stops any active translation.
     */
    public void onVideoChanged(String newVideoId) {
        synchronized (this) {
            if (state != State.IDLE) {
                doStop();
            }
        }
    }

    /** Must be called while holding the lock. */
    private void doStop() {
        cancelled = true;
        syncController.stop();
        duckingManager.stopDucking();

        TranslationAudioManager.State audioState = audioManager.getState();
        if (audioState != TranslationAudioManager.State.IDLE
                && audioState != TranslationAudioManager.State.ERROR) {
            audioManager.stop();
        }

        currentVideoId = null;
        transitionTo(State.IDLE);
    }

    private void transitionTo(State newState) {
        State old = state;
        state = newState;
        if (listener != null && old != newState) {
            listener.onStateChanged(old, newState);
        }
    }
}
