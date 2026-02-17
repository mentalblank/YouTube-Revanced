package app.revanced.integrations.youtube.vot.player;

/**
 * Periodic audio synchronization controller.
 * 
 * Polls the main player position at a configurable interval and calls
 * syncWithMainPlayer on the TranslationAudioManager to keep drift < 500ms.
 * 
 * In the real Android implementation, this would use a Handler with
 * postDelayed. For testability, the tick mechanism is abstracted via
 * the Scheduler interface.
 */
public class AudioSyncController {

    /** Default sync interval in milliseconds */
    public static final long DEFAULT_SYNC_INTERVAL_MS = 1000;

    /**
     * Provides current main player state for sync checks.
     */
    public interface MainPlayerProvider {
        /** Current playback position in ms */
        long getPositionMs();
        /** Whether the main player is currently playing */
        boolean isPlaying();
    }

    /**
     * Abstraction for scheduling periodic callbacks.
     * On Android this would wrap Handler.postDelayed.
     */
    public interface Scheduler {
        void scheduleRepeating(Runnable task, long intervalMs);
        void cancel();
    }

    private final TranslationAudioManager audioManager;
    private final MainPlayerProvider mainPlayer;
    private long syncIntervalMs;
    private Scheduler scheduler;
    private boolean running = false;
    private int syncCount = 0;
    private int seekCount = 0;

    public AudioSyncController(TranslationAudioManager audioManager,
                                MainPlayerProvider mainPlayer) {
        this(audioManager, mainPlayer, DEFAULT_SYNC_INTERVAL_MS);
    }

    public AudioSyncController(TranslationAudioManager audioManager,
                                MainPlayerProvider mainPlayer,
                                long syncIntervalMs) {
        this.audioManager = audioManager;
        this.mainPlayer = mainPlayer;
        this.syncIntervalMs = syncIntervalMs;
    }

    /**
     * Set the scheduler implementation. Must be called before start().
     */
    public void setScheduler(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    /**
     * Start periodic sync. Requires a scheduler to be set.
     */
    public void start() {
        if (running) return;
        if (scheduler == null) {
            throw new IllegalStateException("Scheduler not set. Call setScheduler() first.");
        }
        running = true;
        syncCount = 0;
        seekCount = 0;
        scheduler.scheduleRepeating(this::performSync, syncIntervalMs);
    }

    /**
     * Stop periodic sync.
     */
    public void stop() {
        if (!running) return;
        running = false;
        if (scheduler != null) {
            scheduler.cancel();
        }
    }

    /**
     * Perform a single sync tick. Can be called manually for testing.
     */
    public void performSync() {
        if (!running) return;
        syncCount++;
        boolean seeked = audioManager.syncWithMainPlayer(
            mainPlayer.getPositionMs(),
            mainPlayer.isPlaying()
        );
        if (seeked) {
            seekCount++;
        }
    }

    /** Whether the controller is currently running */
    public boolean isRunning() {
        return running;
    }

    /** Number of sync ticks performed */
    public int getSyncCount() {
        return syncCount;
    }

    /** Number of seek corrections performed */
    public int getSeekCount() {
        return seekCount;
    }

    /** Get configured sync interval */
    public long getSyncIntervalMs() {
        return syncIntervalMs;
    }
}
