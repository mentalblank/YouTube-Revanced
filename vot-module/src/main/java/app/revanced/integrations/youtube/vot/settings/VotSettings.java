package app.revanced.integrations.youtube.vot.settings;

/**
 * Settings for VOT (Voice-Over Translation) module.
 * Manages translation preferences using SharedPreferences pattern.
 *
 * In a full Android environment, this delegates to SharedPreferences.
 * For testability, settings are stored in-memory with the same API.
 */
public class VotSettings {

    private static final String PREF_NAME = "revanced_vot_settings";
    private static final String KEY_ENABLED = "vot_enabled";
    private static final String KEY_TARGET_LANGUAGE = "vot_target_language";
    private static final String KEY_DUCK_VOLUME = "vot_duck_volume";

    // Default values
    public static final boolean DEFAULT_ENABLED = false;
    public static final String DEFAULT_TARGET_LANGUAGE = "ru";
    public static final float DEFAULT_DUCK_VOLUME = 0.15f;

    // Instance fields (in-memory store; mirrors SharedPreferences)
    private boolean enabled;
    private String targetLanguage;
    private float duckVolume;

    private static VotSettings instance;

    public VotSettings() {
        this.enabled = DEFAULT_ENABLED;
        this.targetLanguage = DEFAULT_TARGET_LANGUAGE;
        this.duckVolume = DEFAULT_DUCK_VOLUME;
    }

    /** Get singleton instance */
    public static synchronized VotSettings getInstance() {
        if (instance == null) {
            instance = new VotSettings();
        }
        return instance;
    }

    /** Reset singleton (for testing) */
    public static synchronized void resetInstance() {
        instance = null;
    }

    // --- Getters ---

    /** Check if VOT translation is enabled */
    public boolean isEnabled() {
        return enabled;
    }

    /** Get target translation language code */
    public String getTargetLanguage() {
        return targetLanguage;
    }

    /** Get duck volume (0.0-1.0) — volume of original audio when translation plays */
    public float getDuckVolume() {
        return duckVolume;
    }

    // --- Setters ---

    /** Enable or disable VOT translation */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** Set target translation language code */
    public void setTargetLanguage(String language) {
        if (language == null || language.isEmpty()) {
            throw new IllegalArgumentException("Target language cannot be null or empty");
        }
        this.targetLanguage = language;
    }

    /** Set duck volume (clamped to 0.0-1.0) */
    public void setDuckVolume(float volume) {
        this.duckVolume = Math.max(0f, Math.min(1f, volume));
    }

    // --- Utility ---

    /** Get the SharedPreferences name used by VOT */
    public String getPreferenceName() {
        return PREF_NAME;
    }

    /** Get supported language codes */
    public static String[] getSupportedLanguages() {
        return new String[]{"ru", "en", "zh", "ko", "ja", "de", "fr", "es", "it", "pt", "tr", "ar", "hi", "kk"};
    }

    /** Get preference key for enabled setting */
    public static String getKeyEnabled() { return KEY_ENABLED; }

    /** Get preference key for target language */
    public static String getKeyTargetLanguage() { return KEY_TARGET_LANGUAGE; }

    /** Get preference key for duck volume */
    public static String getKeyDuckVolume() { return KEY_DUCK_VOLUME; }
}
