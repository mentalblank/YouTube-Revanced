package app.revanced.integrations.youtube.vot.settings;

/**
 * Unit tests for VotSettings (US-011).
 * Run: java VotSettingsTest
 */
public class VotSettingsTest {

    private static int passed = 0;
    private static int failed = 0;

    private static void assertEquals(Object expected, Object actual, String test) {
        if (expected == null ? actual == null : expected.equals(actual)) {
            passed++;
            System.out.println("  PASS: " + test);
        } else {
            failed++;
            System.out.println("  FAIL: " + test + " (expected=" + expected + ", actual=" + actual + ")");
        }
    }

    private static void assertTrue(boolean condition, String test) {
        assertEquals(true, condition, test);
    }

    private static void assertFalse(boolean condition, String test) {
        assertEquals(false, condition, test);
    }

    private static void assertFloatEquals(float expected, float actual, float delta, String test) {
        if (Math.abs(expected - actual) <= delta) {
            passed++;
            System.out.println("  PASS: " + test);
        } else {
            failed++;
            System.out.println("  FAIL: " + test + " (expected=" + expected + ", actual=" + actual + ")");
        }
    }

    public static void main(String[] args) {
        System.out.println("=== VotSettings Tests ===\n");

        testDefaultValues();
        testEnabledGetSet();
        testTargetLanguageGetSet();
        testDuckVolumeGetSet();
        testDuckVolumeClamping();
        testInvalidLanguage();
        testSingleton();
        testSupportedLanguages();
        testPreferenceKeys();

        System.out.println("\n=== Results: " + passed + " passed, " + failed + " failed ===");
        if (failed > 0) System.exit(1);
    }

    static void testDefaultValues() {
        System.out.println("Test: Default values");
        VotSettings s = new VotSettings();
        assertFalse(s.isEnabled(), "enabled defaults to false");
        assertEquals("ru", s.getTargetLanguage(), "targetLanguage defaults to 'ru'");
        assertFloatEquals(0.15f, s.getDuckVolume(), 0.001f, "duckVolume defaults to 0.15");
    }

    static void testEnabledGetSet() {
        System.out.println("Test: Enabled get/set round-trip");
        VotSettings s = new VotSettings();
        assertFalse(s.isEnabled(), "initially false");
        s.setEnabled(true);
        assertTrue(s.isEnabled(), "set to true");
        s.setEnabled(false);
        assertFalse(s.isEnabled(), "set back to false");
    }

    static void testTargetLanguageGetSet() {
        System.out.println("Test: Target language get/set round-trip");
        VotSettings s = new VotSettings();
        assertEquals("ru", s.getTargetLanguage(), "default ru");
        s.setTargetLanguage("en");
        assertEquals("en", s.getTargetLanguage(), "changed to en");
        s.setTargetLanguage("ja");
        assertEquals("ja", s.getTargetLanguage(), "changed to ja");
    }

    static void testDuckVolumeGetSet() {
        System.out.println("Test: Duck volume get/set round-trip");
        VotSettings s = new VotSettings();
        assertFloatEquals(0.15f, s.getDuckVolume(), 0.001f, "default 0.15");
        s.setDuckVolume(0.5f);
        assertFloatEquals(0.5f, s.getDuckVolume(), 0.001f, "set to 0.5");
        s.setDuckVolume(0.0f);
        assertFloatEquals(0.0f, s.getDuckVolume(), 0.001f, "set to 0.0");
        s.setDuckVolume(1.0f);
        assertFloatEquals(1.0f, s.getDuckVolume(), 0.001f, "set to 1.0");
    }

    static void testDuckVolumeClamping() {
        System.out.println("Test: Duck volume clamping");
        VotSettings s = new VotSettings();
        s.setDuckVolume(-0.5f);
        assertFloatEquals(0.0f, s.getDuckVolume(), 0.001f, "negative clamped to 0");
        s.setDuckVolume(2.0f);
        assertFloatEquals(1.0f, s.getDuckVolume(), 0.001f, "above 1 clamped to 1");
    }

    static void testInvalidLanguage() {
        System.out.println("Test: Invalid language rejected");
        VotSettings s = new VotSettings();
        boolean caught = false;
        try { s.setTargetLanguage(null); } catch (IllegalArgumentException e) { caught = true; }
        assertTrue(caught, "null language throws IllegalArgumentException");

        caught = false;
        try { s.setTargetLanguage(""); } catch (IllegalArgumentException e) { caught = true; }
        assertTrue(caught, "empty language throws IllegalArgumentException");
    }

    static void testSingleton() {
        System.out.println("Test: Singleton pattern");
        VotSettings.resetInstance();
        VotSettings a = VotSettings.getInstance();
        VotSettings b = VotSettings.getInstance();
        assertTrue(a == b, "getInstance returns same instance");
        a.setEnabled(true);
        assertTrue(b.isEnabled(), "changes visible through singleton");
        VotSettings.resetInstance();
    }

    static void testSupportedLanguages() {
        System.out.println("Test: Supported languages");
        String[] langs = VotSettings.getSupportedLanguages();
        assertTrue(langs.length > 0, "has supported languages");
        assertEquals("ru", langs[0], "first language is ru");
    }

    static void testPreferenceKeys() {
        System.out.println("Test: Preference keys");
        assertEquals("vot_enabled", VotSettings.getKeyEnabled(), "enabled key");
        assertEquals("vot_target_language", VotSettings.getKeyTargetLanguage(), "language key");
        assertEquals("vot_duck_volume", VotSettings.getKeyDuckVolume(), "duck volume key");
    }
}
