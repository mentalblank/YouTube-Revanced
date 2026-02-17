package app.revanced.integrations.youtube.vot.player;

/**
 * Shadow Player — manages a secondary ExoPlayer instance for translation audio overlay.
 * 
 * This class handles the lifecycle of a second audio-only ExoPlayer that plays
 * the translated voice-over audio stream alongside the main video player.
 * 
 * State machine:
 *   IDLE -> LOADING -> READY -> PLAYING -> PAUSED -> PLAYING ...
 *   Any state -> IDLE (via stop/release)
 *   READY/PAUSED -> PLAYING (via play)
 *   PLAYING -> PAUSED (via pause)
 *   Any loaded state -> seekTo allowed
 * 
 * Sync logic is NOT in this class (will be in US-008).
 * Audio ducking volume is managed here but applied externally.
 */
public class TranslationAudioManager {

    /** Maximum allowed drift in milliseconds before seeking shadow player */
    public static final long SYNC_THRESHOLD_MS = 500;

    /** Player states */
    public enum State {
        IDLE,       // No audio loaded, player not initialized
        LOADING,    // Audio URL set, player preparing
        READY,      // Audio prepared, ready to play
        PLAYING,    // Audio is playing
        PAUSED,     // Audio is paused
        ERROR       // An error occurred
    }

    private State state = State.IDLE;
    private String currentAudioUrl = null;
    private float duckVolume = 0.15f;
    private float translationVolume = 1.0f;
    private long currentPositionMs = 0;
    private boolean released = false;

    // In a real implementation, this would be an ExoPlayer instance.
    // For now we use a flag-based approach that can be swapped for real ExoPlayer
    // when integrated into the Android runtime.
    // private ExoPlayer exoPlayer;

    /**
     * Listener interface for state changes.
     */
    public interface StateListener {
        void onStateChanged(State oldState, State newState);
        void onError(String message);
    }

    private StateListener listener;

    public TranslationAudioManager() {
        this.state = State.IDLE;
    }

    /**
     * Set a listener for state changes.
     */
    public void setStateListener(StateListener listener) {
        this.listener = listener;
    }

    /**
     * Load an audio stream from the given URL.
     * Transitions: IDLE/READY/PAUSED/ERROR -> LOADING -> READY
     *
     * @param url The URL of the translation audio stream
     * @throws IllegalStateException if player has been released
     */
    public void loadAudio(String url) {
        checkNotReleased();
        if (url == null || url.isEmpty()) {
            transitionTo(State.ERROR);
            notifyError("Audio URL cannot be null or empty");
            return;
        }

        // Stop current playback if any
        if (state == State.PLAYING || state == State.PAUSED) {
            stopInternal();
        }

        currentAudioUrl = url;
        currentPositionMs = 0;
        transitionTo(State.LOADING);

        // In real implementation: exoPlayer.setMediaItem(MediaItem.fromUri(url));
        // exoPlayer.prepare();
        // The onReady callback would transition to READY.
        // For now, simulate immediate readiness:
        transitionTo(State.READY);
    }

    /**
     * Start or resume playback.
     * Transitions: READY/PAUSED -> PLAYING
     *
     * @throws IllegalStateException if not in READY or PAUSED state, or released
     */
    public void play() {
        checkNotReleased();
        if (state != State.READY && state != State.PAUSED) {
            throw new IllegalStateException(
                "Cannot play in state " + state + ". Must be READY or PAUSED.");
        }
        transitionTo(State.PLAYING);
        // In real implementation: exoPlayer.play();
    }

    /**
     * Pause playback.
     * Transitions: PLAYING -> PAUSED
     *
     * @throws IllegalStateException if not playing, or released
     */
    public void pause() {
        checkNotReleased();
        if (state != State.PLAYING) {
            throw new IllegalStateException(
                "Cannot pause in state " + state + ". Must be PLAYING.");
        }
        transitionTo(State.PAUSED);
        // In real implementation: exoPlayer.pause();
    }

    /**
     * Stop playback and reset to IDLE.
     * Can be called from any state (except released).
     */
    public void stop() {
        checkNotReleased();
        stopInternal();
    }

    private void stopInternal() {
        currentAudioUrl = null;
        currentPositionMs = 0;
        transitionTo(State.IDLE);
        // In real implementation: exoPlayer.stop(); exoPlayer.clearMediaItems();
    }

    /**
     * Seek to a position in the audio stream.
     *
     * @param positionMs Position in milliseconds
     * @throws IllegalStateException if no audio loaded or released
     */
    public void seekTo(long positionMs) {
        checkNotReleased();
        if (state == State.IDLE || state == State.LOADING) {
            throw new IllegalStateException(
                "Cannot seek in state " + state + ". Audio must be loaded.");
        }
        if (positionMs < 0) {
            positionMs = 0;
        }
        currentPositionMs = positionMs;
        // In real implementation: exoPlayer.seekTo(positionMs);
    }

    /**
     * Release the player and free all resources.
     * After release, no methods can be called.
     */
    public void release() {
        if (released) return;
        stopInternal();
        released = true;
        listener = null;
        // In real implementation: exoPlayer.release(); exoPlayer = null;
    }

    // --- Getters ---

    /** Get current player state */
    public State getState() {
        return state;
    }

    /** Check if translation is currently playing */
    public boolean isPlaying() {
        return state == State.PLAYING;
    }

    /** Check if audio is loaded (READY, PLAYING, or PAUSED) */
    public boolean isLoaded() {
        return state == State.READY || state == State.PLAYING || state == State.PAUSED;
    }

    /** Get current audio URL */
    public String getCurrentAudioUrl() {
        return currentAudioUrl;
    }

    /** Get current playback position in ms */
    public long getCurrentPositionMs() {
        return currentPositionMs;
    }

    /** Check if player has been released */
    public boolean isReleased() {
        return released;
    }

    // --- Duck volume (original audio attenuation) ---

    /**
     * Set duck volume for original audio (0.0 = mute, 1.0 = full).
     * Default: 0.15
     */
    public void setDuckVolume(float volume) {
        this.duckVolume = Math.max(0f, Math.min(1f, volume));
    }

    /** Get current duck volume */
    public float getDuckVolume() {
        return duckVolume;
    }

    // --- Translation volume ---

    /**
     * Set translation audio volume (0.0 - 1.0).
     */
    public void setTranslationVolume(float volume) {
        this.translationVolume = Math.max(0f, Math.min(1f, volume));
        // In real implementation: exoPlayer.setVolume(translationVolume);
    }

    /** Get translation audio volume */
    public float getTranslationVolume() {
        return translationVolume;
    }

    // --- Sync logic (US-008) ---

    /**
     * Synchronize shadow player with the main video player position and state.
     * 
     * - If main player is playing and shadow is paused/ready, resume shadow.
     * - If main player is paused and shadow is playing, pause shadow.
     * - If position drift exceeds SYNC_THRESHOLD_MS (500ms), seek shadow player.
     *
     * @param mainPositionMs current position of main video player in ms
     * @param mainIsPlaying  whether the main player is currently playing
     * @return true if a seek correction was performed
     */
    public boolean syncWithMainPlayer(long mainPositionMs, boolean mainIsPlaying) {
        checkNotReleased();
        if (!isLoaded()) {
            return false;
        }

        boolean didSeek = false;

        // Sync play/pause state
        if (mainIsPlaying && state == State.PAUSED) {
            transitionTo(State.PLAYING);
        } else if (mainIsPlaying && state == State.READY) {
            transitionTo(State.PLAYING);
        } else if (!mainIsPlaying && state == State.PLAYING) {
            transitionTo(State.PAUSED);
        }

        // Sync position — correct drift if beyond threshold
        long drift = Math.abs(mainPositionMs - currentPositionMs);
        if (drift > SYNC_THRESHOLD_MS) {
            currentPositionMs = mainPositionMs;
            didSeek = true;
            // In real implementation: exoPlayer.seekTo(mainPositionMs);
        }

        return didSeek;
    }

    // --- Internal ---

    private void transitionTo(State newState) {
        State oldState = this.state;
        this.state = newState;
        if (listener != null && oldState != newState) {
            listener.onStateChanged(oldState, newState);
        }
    }

    private void notifyError(String message) {
        if (listener != null) {
            listener.onError(message);
        }
    }

    private void checkNotReleased() {
        if (released) {
            throw new IllegalStateException("TranslationAudioManager has been released.");
        }
    }
}
