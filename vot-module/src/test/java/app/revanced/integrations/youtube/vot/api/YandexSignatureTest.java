package app.revanced.integrations.youtube.vot.api;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests for YandexSignature HMAC-SHA256 signing.
 * Test vectors generated from reference @vot.js/shared/dist/secure.js
 * using the same HMAC key "bt8xH3VOlb4mqf0nqAibnDOoiPlXsisf".
 */
public class YandexSignatureTest {

    @Test
    public void testSignEmptyBody() {
        byte[] empty = new byte[0];
        String sig = YandexSignature.sign(empty);
        assertEquals("1cf16ec153b873ca0555b3a9e2c20ac155ce09f2c0aaa3a47ef67f4df3f0544e", sig);
    }

    @Test
    public void testSignHelloString() {
        String sig = YandexSignature.sign("hello");
        assertEquals("6309a5cb2434526ac4bc47e7e7d40a4448025c3f5905b554fe36bb327d50f80b", sig);
    }

    @Test
    public void testSignProtobufBytes() {
        byte[] data = new byte[]{0x0a, 0x0b};
        String sig = YandexSignature.sign(data);
        assertEquals("9c648d0da8050bf560486dcb8dda50e521dbb780f97a694f95618103a80d684c", sig);
    }

    @Test
    public void testGetVtransSignature() {
        // Should be identical to sign()
        byte[] data = "hello".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String sig = YandexSignature.getVtransSignature(data);
        assertEquals("6309a5cb2434526ac4bc47e7e7d40a4448025c3f5905b554fe36bb327d50f80b", sig);
    }

    @Test
    public void testBytesToHex() {
        byte[] bytes = {(byte) 0x00, (byte) 0xff, (byte) 0x0a, (byte) 0xbc};
        assertEquals("00ff0abc", YandexSignature.bytesToHex(bytes));
    }

    @Test
    public void testSignatureIs64HexChars() {
        String sig = YandexSignature.sign("test");
        assertEquals(64, sig.length());
        assertTrue(sig.matches("[0-9a-f]{64}"));
    }

    @Test
    public void testGenerateUUID() {
        String uuid = YandexSignature.generateUUID();
        assertEquals(32, uuid.length());
        assertTrue(uuid.matches("[0-9A-F]{32}"));
    }

    @Test
    public void testGetTokenSignature() {
        // Token format: uuid:path:componentVersion
        // Using fixed values to match reference test vector
        String tokenSig = YandexSignature.getTokenSignature("ABC123", "path:/v1/translate");
        // Expected: sign("ABC123:path:/v1/translate:25.6.0.2259") + ":ABC123:path:/v1/translate:25.6.0.2259"
        String expectedSign = "0207dbe40d0671c379df9814374e0f3903fa73c3e037060ff91f4e0d8f0ce73e";
        String expectedToken = "ABC123:path:/v1/translate:" + YandexSignature.COMPONENT_VERSION;
        assertEquals(expectedSign + ":" + expectedToken, tokenSig);
    }
}
