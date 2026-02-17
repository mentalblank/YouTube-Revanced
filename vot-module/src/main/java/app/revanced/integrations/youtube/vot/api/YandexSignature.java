package app.revanced.integrations.youtube.vot.api;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/**
 * HMAC-SHA256 signature generator for Yandex VOT API requests.
 * Generates Vtrans-Signature header and Sec-Vtrans headers.
 *
 * Reference: @vot.js/shared/dist/secure.js
 */
public class YandexSignature {

    private static final String HMAC_KEY = "bt8xH3VOlb4mqf0nqAibnDOoiPlXsisf";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    static final String COMPONENT_VERSION = "25.6.0.2259";

    /**
     * Generate HMAC-SHA256 signature for the given data bytes.
     * Returns the signature as a lowercase hex string.
     */
    public static String sign(byte[] data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(
                    HMAC_KEY.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
            mac.init(keySpec);
            byte[] signature = mac.doFinal(data);
            return bytesToHex(signature);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("Failed to compute HMAC-SHA256 signature", e);
        }
    }

    /**
     * Generate HMAC-SHA256 signature for a string (UTF-8 encoded).
     */
    public static String sign(String data) {
        return sign(data.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Generate the Vtrans-Signature header value for a protobuf request body.
     * This is the primary method used when making translation API calls.
     */
    public static String getVtransSignature(byte[] requestBody) {
        return sign(requestBody);
    }

    /**
     * Generate a token signature for Sec-Vtrans-Token header.
     * Token format: "{uuid}:{path}:{componentVersion}"
     */
    public static String getTokenSignature(String uuid, String path) {
        String token = uuid + ":" + path + ":" + COMPONENT_VERSION;
        String tokenSign = sign(token.getBytes(StandardCharsets.UTF_8));
        return tokenSign + ":" + token;
    }

    /**
     * Generate a random UUID (32 hex chars, uppercase, no dashes).
     */
    public static String generateUUID() {
        String hex = UUID.randomUUID().toString().replace("-", "").toUpperCase();
        return hex;
    }

    /**
     * Convert byte array to lowercase hex string.
     */
    static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xFF));
        }
        return sb.toString();
    }
}
