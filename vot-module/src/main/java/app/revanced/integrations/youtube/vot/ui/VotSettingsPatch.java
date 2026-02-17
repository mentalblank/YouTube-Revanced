package app.revanced.integrations.youtube.vot.ui;

import app.revanced.integrations.youtube.vot.settings.VotSettings;

/**
 * ReVanced patch entry point for injecting VOT settings into
 * YouTube's ReVanced settings screen.
 *
 * Following ReVanced settings conventions:
 * - Static hook methods called from bytecode patches
 * - Settings wired to VotSettings singleton
 * - Provides data for UI rendering (labels, options, ranges)
 *
 * In a real ReVanced integration, the bytecode patch would:
 * 1. Find the ReVanced settings preference screen
 * 2. Add a "VOT Translation" preference category
 * 3. Add: SwitchPreference for enable/disable
 * 4. Add: ListPreference for target language
 * 5. Add: SeekBarPreference for duck volume (0-100%)
 * 6. Wire each preference change to VotSettingsPatch callbacks
 */
public class VotSettingsPatch {

    /** Supported language entries: [code, displayName] pairs */
    public static final String[][] LANGUAGE_OPTIONS = {
            {"ru", "Russian"},
            {"en", "English"},
            {"de", "German"},
            {"fr", "French"},
            {"es", "Spanish"},
            {"it", "Italian"},
            {"pt", "Portuguese"},
            {"zh", "Chinese"},
            {"ja", "Japanese"},
            {"ko", "Korean"},
    };

    /** Settings category title */
    public static final String SETTINGS_CATEGORY_TITLE = "VOT Translation";

    /** Settings item keys matching VotSettings */
    public static final String KEY_ENABLED = "vot_enabled";
    public static final String KEY_TARGET_LANGUAGE = "vot_target_language";
    public static final String KEY_DUCK_VOLUME = "vot_duck_volume";

    /** Duck volume range */
    public static final int DUCK_VOLUME_MIN = 0;
    public static final int DUCK_VOLUME_MAX = 100;

    private static boolean registered = false;

    private VotSettingsPatch() {}

    /**
     * Hook: called from bytecode patch to register VOT settings
     * into ReVanced settings screen.
     *
     * @return true if registration succeeded
     */
    public static boolean registerSettings() {
        registered = true;
        return true;
    }

    /** Check if settings have been registered */
    public static boolean isRegistered() {
        return registered;
    }

    /**
     * Hook: called when the enable translation toggle changes.
     *
     * @param enabled new state
     */
    public static void onEnableChanged(boolean enabled) {
        VotSettings.getInstance().setEnabled(enabled);
    }

    /**
     * Hook: called when the target language selection changes.
     *
     * @param languageCode ISO language code
     */
    public static void onTargetLanguageChanged(String languageCode) {
        if (isValidLanguage(languageCode)) {
            VotSettings.getInstance().setTargetLanguage(languageCode);
        }
    }

    /**
     * Hook: called when the duck volume slider changes.
     *
     * @param percent volume percentage 0-100
     */
    public static void onDuckVolumeChanged(int percent) {
        int clamped = Math.max(DUCK_VOLUME_MIN, Math.min(DUCK_VOLUME_MAX, percent));
        float volume = clamped / 100f;
        VotSettings.getInstance().setDuckVolume(volume);
    }

    /**
     * Get current enable state for UI.
     */
    public static boolean getEnableState() {
        return VotSettings.getInstance().isEnabled();
    }

    /**
     * Get current target language code for UI.
     */
    public static String getTargetLanguageState() {
        return VotSettings.getInstance().getTargetLanguage();
    }

    /**
     * Get current duck volume as percentage (0-100) for UI.
     */
    public static int getDuckVolumePercent() {
        return Math.round(VotSettings.getInstance().getDuckVolume() * 100);
    }

    /**
     * Get language display name for a given code.
     */
    public static String getLanguageDisplayName(String code) {
        for (String[] option : LANGUAGE_OPTIONS) {
            if (option[0].equals(code)) {
                return option[1];
            }
        }
        return code;
    }

    /**
     * Check if a language code is in the supported list.
     */
    public static boolean isValidLanguage(String code) {
        for (String[] option : LANGUAGE_OPTIONS) {
            if (option[0].equals(code)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get all language codes as a flat array.
     */
    public static String[] getLanguageCodes() {
        String[] codes = new String[LANGUAGE_OPTIONS.length];
        for (int i = 0; i < LANGUAGE_OPTIONS.length; i++) {
            codes[i] = LANGUAGE_OPTIONS[i][0];
        }
        return codes;
    }

    /**
     * Get all language display names as a flat array.
     */
    public static String[] getLanguageNames() {
        String[] names = new String[LANGUAGE_OPTIONS.length];
        for (int i = 0; i < LANGUAGE_OPTIONS.length; i++) {
            names[i] = LANGUAGE_OPTIONS[i][1];
        }
        return names;
    }

    /** Reset state (for testing). */
    public static void reset() {
        registered = false;
    }
}
