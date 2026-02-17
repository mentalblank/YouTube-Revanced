package app.revanced.integrations.youtube.vot.api;

import app.revanced.integrations.youtube.vot.proto.TranslationProto;
import app.revanced.integrations.youtube.vot.proto.TranslationProto.TranslationRequest;
import app.revanced.integrations.youtube.vot.proto.TranslationProto.TranslationResponse;
import app.revanced.integrations.youtube.vot.proto.TranslationProto.VideoTranslationStatus;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Client for Yandex Video Translation API.
 * Sends protobuf requests with HMAC-SHA256 signing (Vtrans-Signature header).
 * Implements polling with exponential backoff for pending translations.
 *
 * Reference: vot.js VOTClient, SmartTube YandexTranslationService
 */
public class YandexTranslationClient {

    /** Yandex VOT API endpoint */
    static final String API_URL = "https://api.browser.yandex.ru/video-translation/translate";
    static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 YaBrowser/24.7.0 Safari/537.36";
    static final String ORIGIN = "https://browser.yandex.ru";
    static final String REFERER = "https://browser.yandex.ru/";

    /** Polling configuration */
    static final int MAX_POLL_ATTEMPTS = 30;
    static final long INITIAL_BACKOFF_MS = 1000;
    static final long MAX_BACKOFF_MS = 5000;
    static final double BACKOFF_MULTIPLIER = 1.5;

    /** Connection timeouts */
    static final int CONNECT_TIMEOUT_MS = 10_000;
    static final int READ_TIMEOUT_MS = 30_000;

    /** Allow injection for testing */
    private HttpConnectionFactory connectionFactory;

    /**
     * Abstraction for creating HTTP connections (enables testing with mocks).
     */
    public interface HttpConnectionFactory {
        HttpURLConnection create(String url) throws IOException;
    }

    /** Default factory that opens real HTTP connections. */
    private static class DefaultConnectionFactory implements HttpConnectionFactory {
        @Override
        public HttpURLConnection create(String url) throws IOException {
            return (HttpURLConnection) new URL(url).openConnection();
        }
    }

    /** Sleep function, overridable for testing. */
    Sleeper sleeper = ms -> Thread.sleep(ms);

    interface Sleeper {
        void sleep(long ms) throws InterruptedException;
    }

    public YandexTranslationClient() {
        this.connectionFactory = new DefaultConnectionFactory();
    }

    public YandexTranslationClient(HttpConnectionFactory factory) {
        this.connectionFactory = factory;
    }

    /**
     * Result of a translation request.
     */
    public static class TranslationResult {
        public final String audioUrl;
        public final double duration;
        public final int status;
        public final String translationId;
        public final String message;

        public TranslationResult(String audioUrl, double duration, int status,
                                 String translationId, String message) {
            this.audioUrl = audioUrl;
            this.duration = duration;
            this.status = status;
            this.translationId = translationId;
            this.message = message;
        }

        public boolean isSuccess() {
            return status == VideoTranslationStatus.FINISHED && audioUrl != null && !audioUrl.isEmpty();
        }

        public boolean isPending() {
            return status == VideoTranslationStatus.WAITING
                    || status == VideoTranslationStatus.LONG_WAITING
                    || status == VideoTranslationStatus.AUDIO_REQUESTED;
        }

        public boolean isFailed() {
            return status == VideoTranslationStatus.FAILED;
        }

        @Override
        public String toString() {
            return "TranslationResult{status=" + status + ", audioUrl='" + audioUrl
                    + "', translationId='" + translationId + "'}";
        }
    }

    /**
     * Exception for translation API errors.
     */
    public static class TranslationException extends Exception {
        public final int httpStatus;
        public final int translationStatus;

        public TranslationException(String message, int httpStatus, int translationStatus) {
            super(message);
            this.httpStatus = httpStatus;
            this.translationStatus = translationStatus;
        }

        public TranslationException(String message, Throwable cause) {
            super(message, cause);
            this.httpStatus = -1;
            this.translationStatus = -1;
        }
    }

    /**
     * Request translation for a video URL with polling for completion.
     *
     * @param videoUrl       YouTube video URL
     * @param sourceLang     Source language (e.g. "en")
     * @param targetLang     Target language (e.g. "ru")
     * @param videoDuration  Video duration in seconds
     * @return TranslationResult with audio URL on success
     * @throws TranslationException on API or network errors
     * @throws InterruptedException if polling is interrupted
     */
    public TranslationResult requestTranslation(String videoUrl, String sourceLang,
                                                 String targetLang, double videoDuration)
            throws TranslationException, InterruptedException {

        byte[] requestBody = TranslationProto.buildTranslationRequest(
                videoUrl, sourceLang, targetLang, videoDuration, true);

        TranslationResult result = sendRequest(requestBody);

        if (result.isSuccess()) {
            return result;
        }

        // Poll if pending
        if (result.isPending()) {
            return pollForCompletion(videoUrl, sourceLang, targetLang, videoDuration);
        }

        // Failed
        throw new TranslationException(
                "Translation failed: " + result.message,
                200, result.status);
    }

    /**
     * Poll the API until translation completes or max attempts reached.
     */
    TranslationResult pollForCompletion(String videoUrl, String sourceLang,
                                                String targetLang, double videoDuration)
            throws TranslationException, InterruptedException {

        long backoffMs = INITIAL_BACKOFF_MS;

        for (int attempt = 1; attempt <= MAX_POLL_ATTEMPTS; attempt++) {
            sleeper.sleep(backoffMs);

            byte[] requestBody = TranslationProto.buildTranslationRequest(
                    videoUrl, sourceLang, targetLang, videoDuration, false);

            TranslationResult result = sendRequest(requestBody);

            if (result.isSuccess()) {
                return result;
            }

            if (result.isFailed()) {
                throw new TranslationException(
                        "Translation failed during polling: " + result.message,
                        200, result.status);
            }

            // Still pending, increase backoff
            backoffMs = Math.min((long) (backoffMs * BACKOFF_MULTIPLIER), MAX_BACKOFF_MS);
        }

        throw new TranslationException(
                "Translation timed out after " + MAX_POLL_ATTEMPTS + " polling attempts",
                200, VideoTranslationStatus.WAITING);
    }

    /**
     * Send a single protobuf request to the API and parse the response.
     */
    TranslationResult sendRequest(byte[] requestBody) throws TranslationException {
        String signature = YandexSignature.getVtransSignature(requestBody);
        String uuid = YandexSignature.generateUUID();
        String tokenSignature = YandexSignature.getTokenSignature(uuid, "/video-translation/translate");

        try {
            HttpURLConnection conn = connectionFactory.create(API_URL);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);

            // Headers matching vot.js
            conn.setRequestProperty("Content-Type", "application/x-protobuf");
            conn.setRequestProperty("Accept", "application/x-protobuf");
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setRequestProperty("Origin", ORIGIN);
            conn.setRequestProperty("Referer", REFERER);
            conn.setRequestProperty("Pragma", "no-cache");
            conn.setRequestProperty("Cache-Control", "no-cache");
            conn.setRequestProperty("Vtrans-Signature", signature);
            conn.setRequestProperty("Sec-Vtrans-Token", tokenSignature);

            // Write request body
            try (OutputStream os = conn.getOutputStream()) {
                os.write(requestBody);
                os.flush();
            }

            int httpStatus = conn.getResponseCode();

            if (httpStatus != 200) {
                throw new TranslationException(
                        "HTTP error: " + httpStatus, httpStatus, -1);
            }

            // Read response
            byte[] responseBytes = readAllBytes(conn.getInputStream());
            TranslationResponse response = TranslationProto.parseTranslationResponse(responseBytes);

            return new TranslationResult(
                    response.url,
                    response.duration,
                    response.status,
                    response.translationId,
                    response.message
            );

        } catch (TranslationException e) {
            throw e;
        } catch (IOException e) {
            throw new TranslationException("Network error: " + e.getMessage(), e);
        }
    }

    /**
     * Read all bytes from an InputStream.
     */
    static byte[] readAllBytes(InputStream is) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] tmp = new byte[4096];
        int n;
        while ((n = is.read(tmp)) != -1) {
            buffer.write(tmp, 0, n);
        }
        return buffer.toByteArray();
    }
}
