package app.revanced.integrations.youtube.vot.ui;

import app.revanced.integrations.youtube.vot.settings.VotSettings;

/**
 * Tests for VotSettingsPatch — settings UI wiring.
 */
public class VotSettingsPatchTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        setup();

        testRegisterSettings();
        testEnableToggleWiring();
        testTargetLanguageWiring();
        testDuckVolumeWiring();
        testDuckVolumeClamps();
        testLanguageOptions();
        testLanguageValidation();
        testGetLanguageDisplayName();
        testGetLanguageCodes();
        testGetLanguageNames();
        testGetStateReflectsSettings();
        testInvalidLanguageIgnored();

        System.out.println("\nVotSettingsPatchTest: " + passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    static void setup() {
        VotSettingsPatch.reset();
        VotSettings.resetInstance();
    }

    static void assertEquals(Object expected, Object actual, String name) {
        if (expected == null ? actual == null : expected.equals(actual)) {
            System.out.println("  PASS: " + name);
            passed++;
        } else {
            System.out.println("  FAIL: " + name + " — expected " + expected + " got " + actual);
            failed++;
        }
    }

    static void assertTrue(boolean val, String name) {
        assertEquals(true, val, name);
    }

    static void assertFalse(boolean val, String name) {
        assertEquals(false, val, name);
    }

    static void testRegisterSettings() {
        setup();
        assertFalse(VotSettingsPatch.isRegistered(), "not registered initially");
        VotSettingsPatch.registerSettings();
        assertTrue(VotSettingsPatch.isRegistered(), "registered after call");
    }

    static void testEnableToggleWiring() {
        setup();
        assertFalse(VotSettings.getInstance().isEnabled(), "default disabled");
        VotSettingsPatch.onEnableChanged(true);
        assertTrue(VotSettings.getInstance().isEnabled(), "enabled after toggle");
        assertTrue(VotSettingsPatch.getEnableState(), "getEnableState reflects");
    }

    static void testTargetLanguageWiring() {
        setup();
        assertEquals("ru", VotSettings.getInstance().getTargetLanguage(), "default language ru");
        VotSettingsPatch.onTargetLanguageChanged("en");
        assertEquals("en", VotSettings.getInstance().getTargetLanguage(), "language changed to en");
        assertEquals("en", VotSettingsPatch.getTargetLanguageState(), "getTargetLanguageState reflects");
    }

    static void testDuckVolumeWiring() {
        setup();
        VotSettingsPatch.onDuckVolumeChanged(50);
        assertEquals(0.5f, VotSettings.getInstance().getDuckVolume(), "duck volume 50% = 0.5");
        assertEquals(50, VotSettingsPatch.getDuckVolumePercent(), "getDuckVolumePercent = 50");
    }

    static void testDuckVolumeClamps() {
        setup();
        VotSettingsPatch.onDuckVolumeChanged(-10);
        assertEquals(0, VotSettingsPatch.getDuckVolumePercent(), "clamp below 0");
        VotSettingsPatch.onDuckVolumeChanged(200);
        assertEquals(100, VotSettingsPatch.getDuckVolumePercent(), "clamp above 100");
    }

    static void testLanguageOptions() {
        assertTrue(VotSettingsPatch.LANGUAGE_OPTIONS.length >= 10, "at least 10 language options");
    }

    static void testLanguageValidation() {
        assertTrue(VotSettingsPatch.isValidLanguage("ru"), "ru is valid");
        assertTrue(VotSettingsPatch.isValidLanguage("ko"), "ko is valid");
        assertFalse(VotSettingsPatch.isValidLanguage("xx"), "xx is invalid");
    }

    static void testGetLanguageDisplayName() {
        assertEquals("Russian", VotSettingsPatch.getLanguageDisplayName("ru"), "ru = Russian");
        assertEquals("English", VotSettingsPatch.getLanguageDisplayName("en"), "en = English");
        assertEquals("xx", VotSettingsPatch.getLanguageDisplayName("xx"), "unknown returns code");
    }

    static void testGetLanguageCodes() {
        String[] codes = VotSettingsPatch.getLanguageCodes();
        assertEquals(VotSettingsPatch.LANGUAGE_OPTIONS.length, codes.length, "codes length matches");
        assertEquals("ru", codes[0], "first code is ru");
    }

    static void testGetLanguageNames() {
        String[] names = VotSettingsPatch.getLanguageNames();
        assertEquals(VotSettingsPatch.LANGUAGE_OPTIONS.length, names.length, "names length matches");
        assertEquals("Russian", names[0], "first name is Russian");
    }

    static void testGetStateReflectsSettings() {
        setup();
        // Modify settings directly, check patch reads correctly
        VotSettings.getInstance().setEnabled(true);
        VotSettings.getInstance().setTargetLanguage("ja");
        VotSettings.getInstance().setDuckVolume(0.75f);
        assertTrue(VotSettingsPatch.getEnableState(), "reflects enabled");
        assertEquals("ja", VotSettingsPatch.getTargetLanguageState(), "reflects ja");
        assertEquals(75, VotSettingsPatch.getDuckVolumePercent(), "reflects 75%");
    }

    static void testInvalidLanguageIgnored() {
        setup();
        VotSettingsPatch.onTargetLanguageChanged("en");
        VotSettingsPatch.onTargetLanguageChanged("xx"); // invalid, should be ignored
        assertEquals("en", VotSettings.getInstance().getTargetLanguage(), "invalid language ignored");
    }
}
