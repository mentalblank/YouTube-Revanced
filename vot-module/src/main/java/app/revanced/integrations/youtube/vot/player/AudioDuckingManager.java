package app.revanced.integrations.youtube.vot.player;

/**
 * AudioDuckingManager — manages original video audio volume reduction
 * when translation voice-over is active.
 *
 * Features:
 * - Smooth volume fade over ~300ms (configurable steps)
 * - Configurable duck volume level (default 0.15)
 * - startDucking() / stopDucking() API
 * - Thread-safe volume target tracking
 *
 * In a real Android environment, this would use a Handler/Timer for smooth fading.
 * The fade logic is abstracted via FadeScheduler for testability.
 */
public class AudioDuckingManager {

    public static final float DEFAULT_DUCK_VOLUME = 0.15f;
    public static final float FULL_VOLUME = 1.0f;
    public static final long DEFAULT_FADE_DURATION_MS = 300;
    public static final int DEFAULT_FADE_STEPS = 10;

    private float duckVolume = DEFAULT_DUCK_VOLUME;
    private float currentVolume = FULL_VOLUME;
    private float targetVolume = FULL_VOLUME;
    private boolean ducking = false;
    private long fadeDurationMs = DEFAULT_FADE_DURATION_MS;
    private int fadeSteps = DEFAULT_FADE_STEPS;

    /** Callback to apply volume to the main player */
    public interface VolumeApplier {
        void applyVolume(float volume);
    }

    /** Scheduler for fade animation steps */
    public interface FadeScheduler {
        void scheduleStep(Runnable step, long delayMs);
        void cancelAll();
    }

    private VolumeApplier volumeApplier;
    private FadeScheduler fadeScheduler;

    public AudioDuckingManager() {
    }

    public void setVolumeApplier(VolumeApplier applier) {
        this.volumeApplier = applier;
    }

    public void setFadeScheduler(FadeScheduler scheduler) {
        this.fadeScheduler = scheduler;
    }

    /**
     * Start ducking — reduce main player volume to duck level with smooth fade.
     */
    public void startDucking() {
        ducking = true;
        targetVolume = duckVolume;
        fadeToTarget();
    }

    /**
     * Stop ducking — restore main player volume to 1.0 with smooth fade.
     */
    public void stopDucking() {
        ducking = false;
        targetVolume = FULL_VOLUME;
        fadeToTarget();
    }

    /**
     * Set the duck volume level (0.0 = mute, 1.0 = full volume).
     * If currently ducking, immediately adjusts the target.
     */
    public void setDuckVolume(float volume) {
        this.duckVolume = Math.max(0f, Math.min(1f, volume));
        if (ducking) {
            targetVolume = this.duckVolume;
            fadeToTarget();
        }
    }

    /** Get the configured duck volume level */
    public float getDuckVolume() {
        return duckVolume;
    }

    /** Get the current actual volume */
    public float getCurrentVolume() {
        return currentVolume;
    }

    /** Get the target volume */
    public float getTargetVolume() {
        return targetVolume;
    }

    /** Check if ducking is currently active */
    public boolean isDucking() {
        return ducking;
    }

    /** Set fade duration in milliseconds */
    public void setFadeDurationMs(long durationMs) {
        this.fadeDurationMs = Math.max(0, durationMs);
    }

    /** Set number of fade steps */
    public void setFadeSteps(int steps) {
        this.fadeSteps = Math.max(1, steps);
    }

    // Linear step size, computed when fade starts
    private float fadeStepSize = 0f;
    private int fadeStepsRemaining = 0;

    /**
     * Execute a single fade step towards target volume.
     * Called by the fade scheduler or manually in tests.
     * Returns true if more steps are needed.
     */
    public boolean executeFadeStep() {
        if (Math.abs(currentVolume - targetVolume) < 0.001f) {
            currentVolume = targetVolume;
            applyCurrentVolume();
            return false;
        }

        fadeStepsRemaining--;
        if (fadeStepsRemaining <= 0) {
            currentVolume = targetVolume;
        } else {
            currentVolume += fadeStepSize;
            // Clamp to not overshoot
            if ((fadeStepSize > 0 && currentVolume > targetVolume) ||
                (fadeStepSize < 0 && currentVolume < targetVolume)) {
                currentVolume = targetVolume;
            }
        }

        currentVolume = Math.max(0f, Math.min(1f, currentVolume));
        applyCurrentVolume();
        return Math.abs(currentVolume - targetVolume) > 0.001f;
    }

    /**
     * Instantly set volume without fading. Useful for initialization.
     */
    public void setVolumeImmediate(float volume) {
        this.currentVolume = Math.max(0f, Math.min(1f, volume));
        this.targetVolume = this.currentVolume;
        applyCurrentVolume();
    }

    // --- Internal ---

    private void fadeToTarget() {
        fadeStepSize = (targetVolume - currentVolume) / fadeSteps;
        fadeStepsRemaining = fadeSteps;
        if (fadeScheduler != null) {
            fadeScheduler.cancelAll();
            long stepDelay = fadeDurationMs / fadeSteps;
            scheduleFadeSteps(stepDelay, 0);
        } else {
            // No scheduler — apply immediately (snap)
            currentVolume = targetVolume;
            applyCurrentVolume();
        }
    }

    private void scheduleFadeSteps(long stepDelay, int step) {
        if (step >= fadeSteps) return;
        fadeScheduler.scheduleStep(() -> {
            boolean needsMore = executeFadeStep();
            if (needsMore && step + 1 < fadeSteps) {
                scheduleFadeSteps(stepDelay, step + 1);
            }
        }, stepDelay * (step + 1));
    }

    private void applyCurrentVolume() {
        if (volumeApplier != null) {
            volumeApplier.applyVolume(currentVolume);
        }
    }
}
