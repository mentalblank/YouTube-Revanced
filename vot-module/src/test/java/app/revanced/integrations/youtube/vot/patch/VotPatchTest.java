package app.revanced.integrations.youtube.vot.patch;

import app.revanced.integrations.youtube.vot.VotTranslationCoordinator;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests for VotPatch hook wiring.
 * Verifies that all hooks correctly forward events to the coordinator
 * and that no business logic leaks into the hooks.
 */
public class VotPatchTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        testInitialize();
        testInitializeNullThrows();
        testOnVideoLoadedForwardsEvent();
        testOnVideoLoadedIgnoresWhenNotInitialized();
        testOnVideoLoadedIgnoresNullVideoId();
        testOnVideoLoadedIgnoresEmptyVideoId();
        testOnVideoChangedTriggeredOnDifferentVideo();
        testOnVideoChangedExplicit();
        testOnPlayerStateChangedForwardsEvent();
        testOnPlayerStateEndedStopsTranslation();
        testOnPlayerStateChangedIgnoresWhenNotInitialized();
        testOnSeekForwardsEvent();
        testOnSeekIgnoresWhenNotInitialized();
        testGetCurrentVideoId();
        testResetClearsState();

        System.out.println("\nVotPatchTest: " + passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    private static void assert_(boolean condition, String name) {
        if (condition) {
            System.out.println("  ✓ " + name);
            passed++;
        } else {
            System.out.println("  ✗ " + name);
            failed++;
        }
    }

    // --- Mocks ---

    static class MockCoordinator extends VotTranslationCoordinator {
        List<String> videoChanges = new ArrayList<>();
        int stopCount = 0;

        MockCoordinator() {
            super(null, null, null, null, null, null);
        }

        @Override
        public void onVideoChanged(String newVideoId) {
            videoChanges.add(newVideoId);
        }

        @Override
        public void stopTranslation() {
            stopCount++;
        }
    }

    static class RecordingListener implements VotPatch.PlayerEventListener {
        List<String> videosLoaded = new ArrayList<>();
        List<int[]> stateChanges = new ArrayList<>();
        List<long[]> seeks = new ArrayList<>();
        List<String[]> videoChanges = new ArrayList<>();

        @Override
        public void onVideoLoaded(String videoId) {
            videosLoaded.add(videoId);
        }

        @Override
        public void onPlayerStateChanged(int state) {
            stateChanges.add(new int[]{state});
        }

        @Override
        public void onSeek(long positionMs) {
            seeks.add(new long[]{positionMs});
        }

        @Override
        public void onVideoChanged(String oldVideoId, String newVideoId) {
            videoChanges.add(new String[]{oldVideoId, newVideoId});
        }
    }

    private static MockCoordinator setup() {
        VotPatch.reset();
        MockCoordinator coord = new MockCoordinator();
        VotPatch.initialize(coord);
        return coord;
    }

    // --- Tests ---

    static void testInitialize() {
        VotPatch.reset();
        assert_(!VotPatch.isInitialized(), "not initialized before init");
        MockCoordinator coord = new MockCoordinator();
        VotPatch.initialize(coord);
        assert_(VotPatch.isInitialized(), "initialized after init");
        VotPatch.reset();
    }

    static void testInitializeNullThrows() {
        VotPatch.reset();
        boolean threw = false;
        try {
            VotPatch.initialize(null);
        } catch (IllegalArgumentException e) {
            threw = true;
        }
        assert_(threw, "initialize(null) throws IllegalArgumentException");
        VotPatch.reset();
    }

    static void testOnVideoLoadedForwardsEvent() {
        MockCoordinator coord = setup();
        RecordingListener listener = new RecordingListener();
        VotPatch.setEventListener(listener);

        VotPatch.onVideoLoaded("abc123");
        assert_(listener.videosLoaded.size() == 1, "onVideoLoaded forwarded");
        assert_(listener.videosLoaded.get(0).equals("abc123"), "correct videoId");
        VotPatch.reset();
    }

    static void testOnVideoLoadedIgnoresWhenNotInitialized() {
        VotPatch.reset();
        RecordingListener listener = new RecordingListener();
        VotPatch.setEventListener(listener);
        VotPatch.onVideoLoaded("abc123");
        assert_(listener.videosLoaded.isEmpty(), "ignored when not initialized");
        VotPatch.reset();
    }

    static void testOnVideoLoadedIgnoresNullVideoId() {
        setup();
        RecordingListener listener = new RecordingListener();
        VotPatch.setEventListener(listener);
        VotPatch.onVideoLoaded(null);
        assert_(listener.videosLoaded.isEmpty(), "ignored null videoId");
        VotPatch.reset();
    }

    static void testOnVideoLoadedIgnoresEmptyVideoId() {
        setup();
        RecordingListener listener = new RecordingListener();
        VotPatch.setEventListener(listener);
        VotPatch.onVideoLoaded("");
        assert_(listener.videosLoaded.isEmpty(), "ignored empty videoId");
        VotPatch.reset();
    }

    static void testOnVideoChangedTriggeredOnDifferentVideo() {
        MockCoordinator coord = setup();
        RecordingListener listener = new RecordingListener();
        VotPatch.setEventListener(listener);

        VotPatch.onVideoLoaded("video1");
        VotPatch.onVideoLoaded("video2");

        assert_(coord.videoChanges.size() == 1, "coordinator.onVideoChanged called once");
        assert_(coord.videoChanges.get(0).equals("video2"), "correct new videoId");
        assert_(listener.videoChanges.size() == 1, "listener.onVideoChanged called");
        assert_(listener.videoChanges.get(0)[0].equals("video1"), "old videoId correct");
        assert_(listener.videoChanges.get(0)[1].equals("video2"), "new videoId correct");
        VotPatch.reset();
    }

    static void testOnVideoChangedExplicit() {
        MockCoordinator coord = setup();
        RecordingListener listener = new RecordingListener();
        VotPatch.setEventListener(listener);

        VotPatch.onVideoChanged("newVideo");

        assert_(coord.videoChanges.size() == 1, "explicit onVideoChanged forwarded");
        assert_(listener.videoChanges.size() == 1, "listener notified");
        assert_(VotPatch.getCurrentVideoId().equals("newVideo"), "currentVideoId updated");
        VotPatch.reset();
    }

    static void testOnPlayerStateChangedForwardsEvent() {
        setup();
        RecordingListener listener = new RecordingListener();
        VotPatch.setEventListener(listener);

        VotPatch.onPlayerStateChanged(VotPatch.STATE_PLAYING);
        assert_(listener.stateChanges.size() == 1, "state change forwarded");
        assert_(listener.stateChanges.get(0)[0] == VotPatch.STATE_PLAYING, "correct state");
        VotPatch.reset();
    }

    static void testOnPlayerStateEndedStopsTranslation() {
        MockCoordinator coord = setup();
        VotPatch.onPlayerStateChanged(VotPatch.STATE_ENDED);
        assert_(coord.stopCount == 1, "stopTranslation called on STATE_ENDED");
        VotPatch.reset();
    }

    static void testOnPlayerStateChangedIgnoresWhenNotInitialized() {
        VotPatch.reset();
        RecordingListener listener = new RecordingListener();
        VotPatch.setEventListener(listener);
        VotPatch.onPlayerStateChanged(VotPatch.STATE_PLAYING);
        assert_(listener.stateChanges.isEmpty(), "ignored when not initialized");
        VotPatch.reset();
    }

    static void testOnSeekForwardsEvent() {
        setup();
        RecordingListener listener = new RecordingListener();
        VotPatch.setEventListener(listener);

        VotPatch.onSeek(5000L);
        assert_(listener.seeks.size() == 1, "seek forwarded");
        assert_(listener.seeks.get(0)[0] == 5000L, "correct position");
        VotPatch.reset();
    }

    static void testOnSeekIgnoresWhenNotInitialized() {
        VotPatch.reset();
        RecordingListener listener = new RecordingListener();
        VotPatch.setEventListener(listener);
        VotPatch.onSeek(5000L);
        assert_(listener.seeks.isEmpty(), "ignored when not initialized");
        VotPatch.reset();
    }

    static void testGetCurrentVideoId() {
        setup();
        assert_(VotPatch.getCurrentVideoId() == null, "null before any video");
        VotPatch.onVideoLoaded("test123");
        assert_("test123".equals(VotPatch.getCurrentVideoId()), "correct after load");
        VotPatch.reset();
    }

    static void testResetClearsState() {
        setup();
        VotPatch.onVideoLoaded("test");
        VotPatch.reset();
        assert_(!VotPatch.isInitialized(), "not initialized after reset");
        assert_(VotPatch.getCurrentVideoId() == null, "videoId null after reset");
        VotPatch.reset();
    }
}
