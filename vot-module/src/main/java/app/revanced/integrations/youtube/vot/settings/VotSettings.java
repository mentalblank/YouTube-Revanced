package app.revanced.integrations.youtube.vot.settings;

/**
 * Settings for VOT module.
 * Manages translation language, duck volume, and other preferences.
 */
public class VotSettings {
    private String targetLanguage = "ru";
    private float duckVolume = 0.15f;
    private boolean autoTranslate = false;

    /** Get target translation language */
    public String getTargetLanguage() { return targetLanguage; }

    /** Set target translation language */
    public void setTargetLanguage(String language) { this.targetLanguage = language; }

    /** Get duck volume (0.0-1.0) */
    public float getDuckVolume() { return duckVolume; }

    /** Set duck volume */
    public void setDuckVolume(float volume) { this.duckVolume = Math.max(0f, Math.min(1f, volume)); }

    /** Check if auto-translate is enabled */
    public boolean isAutoTranslate() { return autoTranslate; }

    /** Set auto-translate */
    public void setAutoTranslate(boolean auto) { this.autoTranslate = auto; }

    /** Get supported languages */
    public static String[] getSupportedLanguages() {
        return new String[]{"ru", "en", "zh", "ko", "ja", "de", "fr", "es", "it", "pt", "tr", "ar", "hi", "kk"};
    }
}
