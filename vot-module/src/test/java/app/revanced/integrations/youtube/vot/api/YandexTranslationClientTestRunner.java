package app.revanced.integrations.youtube.vot.api;

import app.revanced.integrations.youtube.vot.proto.TranslationProto;
import app.revanced.integrations.youtube.vot.proto.TranslationProto.TranslationResponse;
import app.revanced.integrations.youtube.vot.proto.TranslationProto.VideoTranslationStatus;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Standalone test runner for YandexTranslationClient (no JUnit dependency).
 * Uses mock HTTP connections to test all scenarios.
 */
public class YandexTranslationClientTestRunner {
    static int passed = 0, failed = 0;

    static void assertEquals(Object expected, Object actual, String testName) {
        if (expected == null ? actual == null : expected.equals(actual)) {
            System.out.println("  PASS: " + testName);
            passed++;
        } else {
            System.out.println("  FAIL: " + testName);
            System.out.println("    Expected: " + expected);
            System.out.println("    Actual:   " + actual);
            failed++;
        }
    }

    static void assertTrue(boolean condition, String testName) {
        if (condition) { System.out.println("  PASS: " + testName); passed++; }
        else { System.out.println("  FAIL: " + testName); failed++; }
    }

    static void assertFalse(boolean condition, String testName) {
        assertTrue(!condition, testName);
    }

    // ==================== Mock HTTP Connection ====================

    static class MockHttpConnection extends HttpURLConnection {
        int mockStatus;
        byte[] mockResponse;
        ByteArrayOutputStream requestBody = new ByteArrayOutputStream();
        Map<String, String> headers = new HashMap<>();

        MockHttpConnection(int status, byte[] response) {
            super(null);
            this.mockStatus = status;
            this.mockResponse = response;
        }

        @Override public void disconnect() {}
        @Override public boolean usingProxy() { return false; }
        @Override public void connect() {}
        @Override public int getResponseCode() { return mockStatus; }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(mockResponse); }
        @Override public OutputStream getOutputStream() { return requestBody; }
        @Override public void setRequestProperty(String key, String value) { headers.put(key, value); }
    }

    static class MockConnectionFactory implements YandexTranslationClient.HttpConnectionFactory {
        List<MockHttpConnection> connections = new ArrayList<>();
        int callIndex = 0;

        void addResponse(int status, byte[] body) {
            connections.add(new MockHttpConnection(status, body));
        }

        @Override
        public HttpURLConnection create(String url) {
            if (callIndex >= connections.size()) {
                throw new RuntimeException("No more mock responses (call #" + callIndex + ")");
            }
            return connections.get(callIndex++);
        }
    }

    // Sleeper that doesn't actually sleep
    static class NoOpSleeper implements YandexTranslationClient.Sleeper {
        int sleepCount = 0;
        @Override public void sleep(long ms) { sleepCount++; }
    }

    // ==================== Helper ====================

    static byte[] makeSuccessResponse(String audioUrl) {
        TranslationResponse resp = new TranslationResponse();
        resp.status = VideoTranslationStatus.FINISHED;
        resp.url = audioUrl;
        resp.duration = 120.0;
        resp.translationId = "test-id-123";
        resp.language = "en";
        return resp.encode();
    }

    static byte[] makePendingResponse() {
        TranslationResponse resp = new TranslationResponse();
        resp.status = VideoTranslationStatus.WAITING;
        resp.remainingTime = 10;
        resp.translationId = "test-id-123";
        return resp.encode();
    }

    static byte[] makeFailedResponse(String message) {
        TranslationResponse resp = new TranslationResponse();
        resp.status = VideoTranslationStatus.FAILED;
        resp.message = message;
        return resp.encode();
    }

    // ==================== Tests ====================

    static void testSuccessfulTranslation() throws Exception {
        System.out.println("\n[Test: Successful translation]");
        MockConnectionFactory factory = new MockConnectionFactory();
        factory.addResponse(200, makeSuccessResponse("https://vtrans.s3.yandex.net/audio.mp3"));

        YandexTranslationClient client = new YandexTranslationClient(factory);

        YandexTranslationClient.TranslationResult result =
                client.requestTranslation("https://youtube.com/watch?v=test", "en", "ru", 120.0);

        assertTrue(result.isSuccess(), "result is success");
        assertEquals("https://vtrans.s3.yandex.net/audio.mp3", result.audioUrl, "audio URL");
        assertEquals(120.0, result.duration, "duration");
        assertEquals("test-id-123", result.translationId, "translationId");
        assertEquals(VideoTranslationStatus.FINISHED, result.status, "status is FINISHED");
        assertFalse(result.isPending(), "not pending");
        assertFalse(result.isFailed(), "not failed");

        // Verify headers were set
        MockHttpConnection conn = factory.connections.get(0);
        assertEquals("application/x-protobuf", conn.headers.get("Content-Type"), "Content-Type header");
        assertTrue(conn.headers.containsKey("Vtrans-Signature"), "has Vtrans-Signature header");
        assertTrue(conn.headers.containsKey("Sec-Vtrans-Token"), "has Sec-Vtrans-Token header");
    }

    static void testPendingThenSuccess() throws Exception {
        System.out.println("\n[Test: Pending then success (polling)]");
        MockConnectionFactory factory = new MockConnectionFactory();
        // First call returns pending
        factory.addResponse(200, makePendingResponse());
        // Poll #1 returns pending
        factory.addResponse(200, makePendingResponse());
        // Poll #2 returns success
        factory.addResponse(200, makeSuccessResponse("https://vtrans.s3.yandex.net/audio2.mp3"));

        YandexTranslationClient client = new YandexTranslationClient(factory);
        NoOpSleeper sleeper = new NoOpSleeper();
        client.sleeper = sleeper;

        YandexTranslationClient.TranslationResult result =
                client.requestTranslation("https://youtube.com/watch?v=test", "en", "ru", 60.0);

        assertTrue(result.isSuccess(), "result is success after polling");
        assertEquals("https://vtrans.s3.yandex.net/audio2.mp3", result.audioUrl, "audio URL after polling");
        assertEquals(2, sleeper.sleepCount, "polled twice before success");
    }

    static void testFailedTranslation() throws Exception {
        System.out.println("\n[Test: Failed translation]");
        MockConnectionFactory factory = new MockConnectionFactory();
        factory.addResponse(200, makeFailedResponse("Unsupported language"));

        YandexTranslationClient client = new YandexTranslationClient(factory);

        try {
            client.requestTranslation("https://youtube.com/watch?v=test", "en", "xx", 60.0);
            assertTrue(false, "should throw TranslationException");
        } catch (YandexTranslationClient.TranslationException e) {
            assertTrue(e.getMessage().contains("Unsupported language"), "error message contains reason");
            assertEquals(200, e.httpStatus, "httpStatus is 200");
            assertEquals(VideoTranslationStatus.FAILED, e.translationStatus, "translationStatus is FAILED");
        }
    }

    static void testHttpError() throws Exception {
        System.out.println("\n[Test: HTTP error]");
        MockConnectionFactory factory = new MockConnectionFactory();
        factory.addResponse(429, new byte[0]);

        YandexTranslationClient client = new YandexTranslationClient(factory);

        try {
            client.requestTranslation("https://youtube.com/watch?v=test", "en", "ru", 60.0);
            assertTrue(false, "should throw TranslationException for HTTP error");
        } catch (YandexTranslationClient.TranslationException e) {
            assertTrue(e.getMessage().contains("429"), "error contains HTTP status");
            assertEquals(429, e.httpStatus, "httpStatus is 429");
        }
    }

    static void testNetworkError() throws Exception {
        System.out.println("\n[Test: Network error]");
        YandexTranslationClient.HttpConnectionFactory factory = url -> {
            throw new IOException("Connection refused");
        };

        YandexTranslationClient client = new YandexTranslationClient(factory);

        try {
            client.requestTranslation("https://youtube.com/watch?v=test", "en", "ru", 60.0);
            assertTrue(false, "should throw TranslationException for network error");
        } catch (YandexTranslationClient.TranslationException e) {
            assertTrue(e.getMessage().contains("Network error"), "error is network error");
            assertEquals(-1, e.httpStatus, "httpStatus is -1 for network errors");
        }
    }

    static void testFailDuringPolling() throws Exception {
        System.out.println("\n[Test: Failure during polling]");
        MockConnectionFactory factory = new MockConnectionFactory();
        factory.addResponse(200, makePendingResponse());  // initial
        factory.addResponse(200, makeFailedResponse("Server error"));  // poll fails

        YandexTranslationClient client = new YandexTranslationClient(factory);
        client.sleeper = ms -> {};

        try {
            client.requestTranslation("https://youtube.com/watch?v=test", "en", "ru", 60.0);
            assertTrue(false, "should throw on poll failure");
        } catch (YandexTranslationClient.TranslationException e) {
            assertTrue(e.getMessage().contains("polling"), "mentions polling");
        }
    }

    static void testTranslationResultStatuses() {
        System.out.println("\n[Test: TranslationResult status helpers]");

        YandexTranslationClient.TranslationResult success =
                new YandexTranslationClient.TranslationResult("url", 60, VideoTranslationStatus.FINISHED, "id", null);
        assertTrue(success.isSuccess(), "FINISHED with URL is success");
        assertFalse(success.isPending(), "FINISHED is not pending");
        assertFalse(success.isFailed(), "FINISHED is not failed");

        YandexTranslationClient.TranslationResult waiting =
                new YandexTranslationClient.TranslationResult(null, 0, VideoTranslationStatus.WAITING, "id", null);
        assertFalse(waiting.isSuccess(), "WAITING is not success");
        assertTrue(waiting.isPending(), "WAITING is pending");

        YandexTranslationClient.TranslationResult longWait =
                new YandexTranslationClient.TranslationResult(null, 0, VideoTranslationStatus.LONG_WAITING, "id", null);
        assertTrue(longWait.isPending(), "LONG_WAITING is pending");

        YandexTranslationClient.TranslationResult audioReq =
                new YandexTranslationClient.TranslationResult(null, 0, VideoTranslationStatus.AUDIO_REQUESTED, "id", null);
        assertTrue(audioReq.isPending(), "AUDIO_REQUESTED is pending");

        YandexTranslationClient.TranslationResult fail =
                new YandexTranslationClient.TranslationResult(null, 0, VideoTranslationStatus.FAILED, "id", "err");
        assertTrue(fail.isFailed(), "FAILED is failed");
        assertFalse(fail.isSuccess(), "FAILED is not success");
    }

    static void testRequestSendsProtobufBody() throws Exception {
        System.out.println("\n[Test: Request sends protobuf body]");
        MockConnectionFactory factory = new MockConnectionFactory();
        factory.addResponse(200, makeSuccessResponse("https://example.com/audio.mp3"));

        YandexTranslationClient client = new YandexTranslationClient(factory);
        client.requestTranslation("https://youtube.com/watch?v=abc", "en", "ru", 300.0);

        byte[] sentBody = factory.connections.get(0).requestBody.toByteArray();
        assertTrue(sentBody.length > 0, "request body is not empty");

        // Decode and verify
        TranslationProto.TranslationRequest decoded = TranslationProto.TranslationRequest.decode(sentBody);
        assertEquals("https://youtube.com/watch?v=abc", decoded.url, "request URL in protobuf");
        assertEquals("en", decoded.language, "source language in protobuf");
        assertEquals("ru", decoded.responseLanguage, "target language in protobuf");
        assertEquals(300.0, decoded.duration, "duration in protobuf");
        assertTrue(decoded.firstRequest, "firstRequest is true");
    }

    static void testConstants() {
        System.out.println("\n[Test: Client constants]");
        assertEquals("https://api.browser.yandex.ru/video-translation/translate",
                YandexTranslationClient.API_URL, "API URL");
        assertTrue(YandexTranslationClient.MAX_POLL_ATTEMPTS > 0, "max poll attempts > 0");
        assertTrue(YandexTranslationClient.INITIAL_BACKOFF_MS > 0, "initial backoff > 0");
        assertTrue(YandexTranslationClient.MAX_BACKOFF_MS >= YandexTranslationClient.INITIAL_BACKOFF_MS,
                "max backoff >= initial");
        assertTrue(YandexTranslationClient.BACKOFF_MULTIPLIER > 1.0, "backoff multiplier > 1");
    }

    // ==================== Main ====================

    public static void main(String[] args) throws Exception {
        System.out.println("YandexTranslationClient Tests:");

        testSuccessfulTranslation();
        testPendingThenSuccess();
        testFailedTranslation();
        testHttpError();
        testNetworkError();
        testFailDuringPolling();
        testTranslationResultStatuses();
        testRequestSendsProtobufBody();
        testConstants();

        System.out.println("\nResults: " + passed + " passed, " + failed + " failed");
        System.exit(failed > 0 ? 1 : 0);
    }
}
