package app.revanced.integrations.youtube.vot.api;

import java.nio.charset.StandardCharsets;

/**
 * Standalone test runner for YandexSignature (no JUnit dependency).
 */
public class YandexSignatureTestRunner {
    static int passed = 0, failed = 0;

    static void assertEquals(String expected, String actual, String testName) {
        if (expected.equals(actual)) {
            System.out.println("  PASS: " + testName);
            passed++;
        } else {
            System.out.println("  FAIL: " + testName);
            System.out.println("    Expected: " + expected);
            System.out.println("    Actual:   " + actual);
            failed++;
        }
    }

    static void assertEquals(int expected, int actual, String testName) {
        assertEquals(String.valueOf(expected), String.valueOf(actual), testName);
    }

    static void assertTrue(boolean condition, String testName) {
        if (condition) { System.out.println("  PASS: " + testName); passed++; }
        else { System.out.println("  FAIL: " + testName); failed++; }
    }

    public static void main(String[] args) {
        System.out.println("YandexSignature Tests:");

        // Test 1: sign empty body
        assertEquals("1cf16ec153b873ca0555b3a9e2c20ac155ce09f2c0aaa3a47ef67f4df3f0544e",
                YandexSignature.sign(new byte[0]), "sign empty body");

        // Test 2: sign 'hello'
        assertEquals("6309a5cb2434526ac4bc47e7e7d40a4448025c3f5905b554fe36bb327d50f80b",
                YandexSignature.sign("hello"), "sign 'hello'");

        // Test 3: sign protobuf-like bytes
        assertEquals("9c648d0da8050bf560486dcb8dda50e521dbb780f97a694f95618103a80d684c",
                YandexSignature.sign(new byte[]{0x0a, 0x0b}), "sign protobuf bytes");

        // Test 4: getVtransSignature == sign
        assertEquals("6309a5cb2434526ac4bc47e7e7d40a4448025c3f5905b554fe36bb327d50f80b",
                YandexSignature.getVtransSignature("hello".getBytes(StandardCharsets.UTF_8)),
                "getVtransSignature matches sign");

        // Test 5: bytesToHex
        assertEquals("00ff0abc",
                YandexSignature.bytesToHex(new byte[]{0x00, (byte)0xff, 0x0a, (byte)0xbc}),
                "bytesToHex");

        // Test 6: signature is 64 hex chars
        String sig = YandexSignature.sign("test");
        assertEquals(64, sig.length(), "signature length is 64");
        assertTrue(sig.matches("[0-9a-f]{64}"), "signature is hex");

        // Test 7: generateUUID format
        String uuid = YandexSignature.generateUUID();
        assertEquals(32, uuid.length(), "UUID length is 32");
        assertTrue(uuid.matches("[0-9A-F]{32}"), "UUID is uppercase hex");

        // Test 8: token signature
        String tokenSig = YandexSignature.getTokenSignature("ABC123", "path:/v1/translate");
        String expected = "0207dbe40d0671c379df9814374e0f3903fa73c3e037060ff91f4e0d8f0ce73e:ABC123:path:/v1/translate:25.6.0.2259";
        assertEquals(expected, tokenSig, "token signature");

        System.out.println("\nResults: " + passed + " passed, " + failed + " failed");
        System.exit(failed > 0 ? 1 : 0);
    }
}
