package app.revanced.integrations.youtube.vot.patch;

import app.revanced.integrations.youtube.vot.VotTranslationCoordinator;

/**
 * Main ReVanced patch entry point for VOT integration.
 *
 * This class provides static hook methods that are called from ReVanced bytecode patches.
 * All hooks are minimal event-forwarding — no business logic lives here.
 *
 * Bytecode patches inject calls to these static methods at:
 * - Video load → onVideoLoaded(videoId)
 * - Player state change → onPlayerStateChanged(state)
 * - Seek → onSeek(positionMs)
 * - Video change → onVideoChanged(newVideoId)
 *
 * All events are forwarded to VotTranslationCoordinator.
 */
public class VotPatch {

    /** Player state constants matching ExoPlayer / YouTube internals. */
    public static final int STATE_PLAYING = 1;
    public static final int STATE_PAUSED = 2;
    public static final int STATE_ENDED = 3;
    public static final int STATE_BUFFERING = 4;

    private static VotTranslationCoordinator coordinator;
    private static PlayerEventListener eventListener;
    private static String currentVideoId;
    private static boolean initialized = false;

    /** Listener for player events — used for testing and extensibility. */
    public interface PlayerEventListener {
        void onVideoLoaded(String videoId);
        void onPlayerStateChanged(int state);
        void onSeek(long positionMs);
        void onVideoChanged(String oldVideoId, String newVideoId);
    }

    private VotPatch() {}

    /**
     * Initialize the patch with a coordinator.
     * Must be called once during app startup / player initialization.
     */
    public static void initialize(VotTranslationCoordinator coordinator) {
        if (coordinator == null) {
            throw new IllegalArgumentException("coordinator must not be null");
        }
        VotPatch.coordinator = coordinator;
        VotPatch.initialized = true;
    }

    /**
     * Set an optional event listener (for testing or monitoring).
     */
    public static void setEventListener(PlayerEventListener listener) {
        VotPatch.eventListener = listener;
    }

    /**
     * Hook: called when a video is loaded in the player.
     * Bytecode patch injects this call when YouTube resolves a video ID.
     */
    public static void onVideoLoaded(String videoId) {
        if (!initialized || videoId == null || videoId.isEmpty()) return;

        String oldVideoId = currentVideoId;
        currentVideoId = videoId;

        // If video changed, notify coordinator
        if (oldVideoId != null && !oldVideoId.equals(videoId)) {
            coordinator.onVideoChanged(videoId);
            if (eventListener != null) {
                eventListener.onVideoChanged(oldVideoId, videoId);
            }
        }

        if (eventListener != null) {
            eventListener.onVideoLoaded(videoId);
        }
    }

    /**
     * Hook: called when player state changes (play/pause/end/buffer).
     */
    public static void onPlayerStateChanged(int state) {
        if (!initialized) return;

        if (state == STATE_ENDED) {
            coordinator.stopTranslation();
        }

        if (eventListener != null) {
            eventListener.onPlayerStateChanged(state);
        }
    }

    /**
     * Hook: called when user seeks to a new position.
     */
    public static void onSeek(long positionMs) {
        if (!initialized) return;

        // Coordinator's sync controller handles re-sync internally,
        // but we forward the event for awareness
        if (eventListener != null) {
            eventListener.onSeek(positionMs);
        }
    }

    /**
     * Hook: called when the video changes (e.g., next video in playlist).
     * This is a more explicit signal than onVideoLoaded for cases where
     * the old video ID isn't tracked yet.
     */
    public static void onVideoChanged(String newVideoId) {
        if (!initialized) return;

        String oldVideoId = currentVideoId;
        currentVideoId = newVideoId;

        coordinator.onVideoChanged(newVideoId);

        if (eventListener != null) {
            eventListener.onVideoChanged(oldVideoId, newVideoId);
        }
    }

    /** Get current video ID. */
    public static String getCurrentVideoId() {
        return currentVideoId;
    }

    /** Check if initialized. */
    public static boolean isInitialized() {
        return initialized;
    }

    /** Reset all state (for testing). */
    public static void reset() {
        coordinator = null;
        eventListener = null;
        currentVideoId = null;
        initialized = false;
    }
}
