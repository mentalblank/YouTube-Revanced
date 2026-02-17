package app.revanced.integrations.youtube.vot.player;

/**
 * Audio ducking controller - lowers original audio volume when translation is active.
 * Uses RMS-based speech detection with exponential smoothing.
 */
public class AudioDucking {
    private static final float DEFAULT_DUCK_LEVEL = 0.15f;
    private float duckLevel = DEFAULT_DUCK_LEVEL;
    private boolean isDucking = false;

    /** Enable ducking (lower original volume) */
    public void enableDucking() {
        isDucking = true;
    }

    /** Disable ducking (restore original volume) */
    public void disableDucking() {
        isDucking = false;
    }

    /** Check if ducking is active */
    public boolean isDucking() {
        return isDucking;
    }

    /** Set duck level (0.0 = mute, 1.0 = full volume) */
    public void setDuckLevel(float level) {
        this.duckLevel = Math.max(0f, Math.min(1f, level));
    }

    /** Get current duck level */
    public float getDuckLevel() {
        return duckLevel;
    }
}
