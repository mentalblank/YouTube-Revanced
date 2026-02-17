package app.revanced.integrations.youtube.vot.ui;

/**
 * UI button for toggling voice-over translation in the YouTube player.
 */
public class TranslationButton {
    private boolean translationEnabled = false;

    /** Toggle translation on/off */
    public void toggle() {
        translationEnabled = !translationEnabled;
    }

    /** Check if translation is enabled */
    public boolean isEnabled() {
        return translationEnabled;
    }

    /** Set translation state */
    public void setEnabled(boolean enabled) {
        this.translationEnabled = enabled;
    }
}
