package app.revanced.integrations.youtube.vot;

import app.revanced.integrations.youtube.vot.api.YandexTranslationClient;
import app.revanced.integrations.youtube.vot.api.YandexTranslationClient.TranslationResult;
import app.revanced.integrations.youtube.vot.player.AudioDuckingManager;
import app.revanced.integrations.youtube.vot.player.AudioSyncController;
import app.revanced.integrations.youtube.vot.player.TranslationAudioManager;
import app.revanced.integrations.youtube.vot.proto.TranslationProto.VideoTranslationStatus;

import java.util.ArrayList;
import java.util.List;

/**
 * Unit tests for VotTranslationCoordinator (US-010).
 * Uses mocked dependencies to verify state transitions and coordination.
 * Run: java VotTranslationCoordinatorTest
 */
public class VotTranslationCoordinatorTest {

    private static int passed = 0;
    private static int failed = 0;

    // --- Mock/Fake classes ---

    static class FakeTranslationClient extends YandexTranslationClient {
        TranslationResult nextResult;
        Exception nextException;
        int requestCount = 0;

        FakeTranslationClient() { super(); }

        @Override
        public TranslationResult requestTranslation(String videoUrl, String sourceLang,
                String targetLang, double videoDuration) throws YandexTranslationClient.TranslationException, InterruptedException {
            requestCount++;
            if (nextException instanceof YandexTranslationClient.TranslationException) {
                throw (YandexTranslationClient.TranslationException) nextException;
            }
            if (nextException instanceof InterruptedException) {
                throw (InterruptedException) nextException;
            }
            if (nextException != null) {
                throw new RuntimeException(nextException);
            }
            return nextResult;
        }
    }

    /** Synchronous executor for testing — runs immediately */
    static class SyncExecutor implements VotTranslationCoordinator.Executor {
        @Override public void execute(Runnable task) { task.run(); }
    }

    /** Synchronous main thread poster — runs immediately */
    static class SyncPoster implements VotTranslationCoordinator.MainThreadPoster {
        @Override public void post(Runnable task) { task.run(); }
    }

    static class StateRecorder implements VotTranslationCoordinator.StateListener {
        List<VotTranslationCoordinator.State> states = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        @Override
        public void onStateChanged(VotTranslationCoordinator.State oldState, VotTranslationCoordinator.State newState) {
            states.add(newState);
        }

        @Override
        public void onError(String message) {
            errors.add(message);
        }
    }

    // --- Helper ---

    static TranslationResult successResult(String audioUrl) {
        return new TranslationResult(audioUrl, 120.0, VideoTranslationStatus.FINISHED, "tid-1", null);
    }

    static TranslationResult failedResult() {
        return new TranslationResult(null, 0, VideoTranslationStatus.FAILED, null, "Translation failed");
    }

    static VotTranslationCoordinator createCoordinator(FakeTranslationClient client, StateRecorder recorder) {
        TranslationAudioManager audioManager = new TranslationAudioManager();
        AudioDuckingManager duckingManager = new AudioDuckingManager();

        // AudioSyncController needs interfaces; provide no-ops
        AudioSyncController.MainPlayerProvider provider = new AudioSyncController.MainPlayerProvider() {
            @Override public long getPositionMs() { return 0; }
            @Override public boolean isPlaying() { return false; }
        };
        AudioSyncController syncController = new AudioSyncController(audioManager, provider);
        syncController.setScheduler(new AudioSyncController.Scheduler() {
            @Override public void scheduleRepeating(Runnable task, long intervalMs) {}
            @Override public void cancel() {}
        });

        VotTranslationCoordinator coord = new VotTranslationCoordinator(
                client, audioManager, duckingManager, syncController,
                new SyncExecutor(), new SyncPoster());
        coord.setStateListener(recorder);
        return coord;
    }

    // --- Tests ---

    public static void main(String[] args) {
        testInitialStateIsIdle();
        testSuccessfulTranslationFlow();
        testStateTransitionsOnSuccess();
        testStopTranslationRestoresIdle();
        testStopWhenIdleIsNoop();
        testVideoChangeStopsCurrentTranslation();
        testTranslationErrorSetsErrorState();
        testInterruptedExceptionHandled();
        testApiFailureResultSetsError();
        testStartTranslationWithNullVideoIdThrows();
        testStartTranslationWithEmptyLanguageThrows();
        testDoubleStartStopsFirst();

        System.out.println("\n=== VotTranslationCoordinator Tests: " + passed + " passed, " + failed + " failed ===");
        if (failed > 0) {
            System.exit(1);
        }
    }

    static void testInitialStateIsIdle() {
        FakeTranslationClient client = new FakeTranslationClient();
        StateRecorder recorder = new StateRecorder();
        VotTranslationCoordinator coord = createCoordinator(client, recorder);
        assertEquals("initialState", VotTranslationCoordinator.State.IDLE, coord.getState());
    }

    static void testSuccessfulTranslationFlow() {
        FakeTranslationClient client = new FakeTranslationClient();
        client.nextResult = successResult("https://audio.example.com/tr.mp3");
        StateRecorder recorder = new StateRecorder();
        VotTranslationCoordinator coord = createCoordinator(client, recorder);

        coord.startTranslation("dQw4w9WgXcQ", "ru");

        assertEquals("finalState", VotTranslationCoordinator.State.PLAYING, coord.getState());
        assertEquals("requestCount", 1, client.requestCount);
        assertEquals("currentVideoId", "dQw4w9WgXcQ", coord.getCurrentVideoId());
    }

    static void testStateTransitionsOnSuccess() {
        FakeTranslationClient client = new FakeTranslationClient();
        client.nextResult = successResult("https://audio.example.com/tr.mp3");
        StateRecorder recorder = new StateRecorder();
        VotTranslationCoordinator coord = createCoordinator(client, recorder);

        coord.startTranslation("vid1", "ru");

        // Should see: REQUESTING, LOADING, PLAYING
        assertEquals("stateCount", 3, recorder.states.size());
        assertEquals("state0", VotTranslationCoordinator.State.REQUESTING, recorder.states.get(0));
        assertEquals("state1", VotTranslationCoordinator.State.LOADING, recorder.states.get(1));
        assertEquals("state2", VotTranslationCoordinator.State.PLAYING, recorder.states.get(2));
    }

    static void testStopTranslationRestoresIdle() {
        FakeTranslationClient client = new FakeTranslationClient();
        client.nextResult = successResult("https://audio.example.com/tr.mp3");
        StateRecorder recorder = new StateRecorder();
        VotTranslationCoordinator coord = createCoordinator(client, recorder);

        coord.startTranslation("vid1", "ru");
        coord.stopTranslation();

        assertEquals("stateAfterStop", VotTranslationCoordinator.State.IDLE, coord.getState());
        assertEquals("videoIdCleared", null, coord.getCurrentVideoId());
    }

    static void testStopWhenIdleIsNoop() {
        FakeTranslationClient client = new FakeTranslationClient();
        StateRecorder recorder = new StateRecorder();
        VotTranslationCoordinator coord = createCoordinator(client, recorder);

        coord.stopTranslation(); // Should not throw
        assertEquals("stillIdle", VotTranslationCoordinator.State.IDLE, coord.getState());
        assertEquals("noStateChanges", 0, recorder.states.size());
    }

    static void testVideoChangeStopsCurrentTranslation() {
        FakeTranslationClient client = new FakeTranslationClient();
        client.nextResult = successResult("https://audio.example.com/tr.mp3");
        StateRecorder recorder = new StateRecorder();
        VotTranslationCoordinator coord = createCoordinator(client, recorder);

        coord.startTranslation("vid1", "ru");
        assertEquals("playing", VotTranslationCoordinator.State.PLAYING, coord.getState());

        coord.onVideoChanged("vid2");
        assertEquals("idleAfterVideoChange", VotTranslationCoordinator.State.IDLE, coord.getState());
        assertEquals("videoIdCleared", null, coord.getCurrentVideoId());
    }

    static void testTranslationErrorSetsErrorState() {
        FakeTranslationClient client = new FakeTranslationClient();
        client.nextException = new RuntimeException("Network error");
        StateRecorder recorder = new StateRecorder();
        VotTranslationCoordinator coord = createCoordinator(client, recorder);

        coord.startTranslation("vid1", "ru");

        assertEquals("errorState", VotTranslationCoordinator.State.ERROR, coord.getState());
        assertTrue("hasError", recorder.errors.size() > 0);
    }

    static void testInterruptedExceptionHandled() {
        FakeTranslationClient client = new FakeTranslationClient();
        client.nextException = new InterruptedException("Interrupted");
        StateRecorder recorder = new StateRecorder();
        VotTranslationCoordinator coord = createCoordinator(client, recorder);

        coord.startTranslation("vid1", "ru");

        assertEquals("errorState", VotTranslationCoordinator.State.ERROR, coord.getState());
    }

    static void testApiFailureResultSetsError() {
        FakeTranslationClient client = new FakeTranslationClient();
        client.nextResult = failedResult();
        StateRecorder recorder = new StateRecorder();
        VotTranslationCoordinator coord = createCoordinator(client, recorder);

        coord.startTranslation("vid1", "ru");

        assertEquals("errorState", VotTranslationCoordinator.State.ERROR, coord.getState());
    }

    static void testStartTranslationWithNullVideoIdThrows() {
        FakeTranslationClient client = new FakeTranslationClient();
        StateRecorder recorder = new StateRecorder();
        VotTranslationCoordinator coord = createCoordinator(client, recorder);

        try {
            coord.startTranslation(null, "ru");
            fail("shouldThrow");
        } catch (IllegalArgumentException e) {
            pass("nullVideoIdThrows");
        }
    }

    static void testStartTranslationWithEmptyLanguageThrows() {
        FakeTranslationClient client = new FakeTranslationClient();
        StateRecorder recorder = new StateRecorder();
        VotTranslationCoordinator coord = createCoordinator(client, recorder);

        try {
            coord.startTranslation("vid1", "");
            fail("shouldThrow");
        } catch (IllegalArgumentException e) {
            pass("emptyLanguageThrows");
        }
    }

    static void testDoubleStartStopsFirst() {
        FakeTranslationClient client = new FakeTranslationClient();
        client.nextResult = successResult("https://audio.example.com/tr.mp3");
        StateRecorder recorder = new StateRecorder();
        VotTranslationCoordinator coord = createCoordinator(client, recorder);

        coord.startTranslation("vid1", "ru");
        assertEquals("playing1", VotTranslationCoordinator.State.PLAYING, coord.getState());

        // Start second translation — should stop first
        coord.startTranslation("vid2", "ru");
        assertEquals("playing2", VotTranslationCoordinator.State.PLAYING, coord.getState());
        assertEquals("newVideoId", "vid2", coord.getCurrentVideoId());
        assertEquals("twoRequests", 2, client.requestCount);
    }

    // --- Assertion helpers ---

    static void assertEquals(String name, Object expected, Object actual) {
        if (expected == null ? actual == null : expected.equals(actual)) {
            passed++;
        } else {
            failed++;
            System.out.println("FAIL " + name + ": expected " + expected + " but got " + actual);
        }
    }

    static void assertEquals(String name, int expected, int actual) {
        if (expected == actual) { passed++; }
        else { failed++; System.out.println("FAIL " + name + ": expected " + expected + " but got " + actual); }
    }

    static void assertTrue(String name, boolean condition) {
        if (condition) { passed++; }
        else { failed++; System.out.println("FAIL " + name); }
    }

    static void pass(String name) { passed++; }
    static void fail(String name) { failed++; System.out.println("FAIL " + name); }
}
