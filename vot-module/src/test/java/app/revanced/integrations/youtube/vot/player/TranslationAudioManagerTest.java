package app.revanced.integrations.youtube.vot.player;

import app.revanced.integrations.youtube.vot.player.TranslationAudioManager;
import app.revanced.integrations.youtube.vot.player.TranslationAudioManager.State;

/**
 * Unit tests for TranslationAudioManager state machine.
 * Uses simple assertions (no JUnit dependency required).
 * Run: java TranslationAudioManagerTest
 */
public class TranslationAudioManagerTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        testInitialState();
        testLoadAudio();
        testPlayAfterLoad();
        testPauseAfterPlay();
        testResumeAfterPause();
        testStop();
        testSeekTo();
        testSeekNegativeClampsToZero();
        testCannotPlayBeforeLoad();
        testCannotPauseWhenNotPlaying();
        testCannotSeekWhenIdle();
        testRelease();
        testCannotUseAfterRelease();
        testLoadNewAudioWhilePlaying();
        testDuckVolumeClamping();
        testTranslationVolumeClamping();
        testLoadNullUrl();
        testStateListener();
        testDoubleRelease();
        testStopFromAnyState();

        System.out.println("\n=== Results: " + passed + " passed, " + failed + " failed ===");
        if (failed > 0) {
            System.exit(1);
        }
    }

    static void testInitialState() {
        TranslationAudioManager mgr = new TranslationAudioManager();
        assertEquals("initial state", State.IDLE, mgr.getState());
        assertEquals("not playing", false, mgr.isPlaying());
        assertEquals("not loaded", false, mgr.isLoaded());
        assertEquals("not released", false, mgr.isReleased());
        assertEquals("duck volume default", 0.15f, mgr.getDuckVolume());
    }

    static void testLoadAudio() {
        TranslationAudioManager mgr = new TranslationAudioManager();
        mgr.loadAudio("https://example.com/audio.mp3");
        assertEquals("state after load", State.READY, mgr.getState());
        assertEquals("url stored", "https://example.com/audio.mp3", mgr.getCurrentAudioUrl());
        assertEquals("is loaded", true, mgr.isLoaded());
        assertEquals("position reset", 0L, mgr.getCurrentPositionMs());
    }

    static void testPlayAfterLoad() {
        TranslationAudioManager mgr = new TranslationAudioManager();
        mgr.loadAudio("https://example.com/audio.mp3");
        mgr.play();
        assertEquals("state playing", State.PLAYING, mgr.getState());
        assertEquals("isPlaying", true, mgr.isPlaying());
    }

    static void testPauseAfterPlay() {
        TranslationAudioManager mgr = new TranslationAudioManager();
        mgr.loadAudio("https://example.com/audio.mp3");
        mgr.play();
        mgr.pause();
        assertEquals("state paused", State.PAUSED, mgr.getState());
        assertEquals("not playing", false, mgr.isPlaying());
        assertEquals("still loaded", true, mgr.isLoaded());
    }

    static void testResumeAfterPause() {
        TranslationAudioManager mgr = new TranslationAudioManager();
        mgr.loadAudio("https://example.com/audio.mp3");
        mgr.play();
        mgr.pause();
        mgr.play();
        assertEquals("resumed playing", State.PLAYING, mgr.getState());
    }

    static void testStop() {
        TranslationAudioManager mgr = new TranslationAudioManager();
        mgr.loadAudio("https://example.com/audio.mp3");
        mgr.play();
        mgr.stop();
        assertEquals("state idle after stop", State.IDLE, mgr.getState());
        assertEquals("url cleared", null, mgr.getCurrentAudioUrl());
        assertEquals("not loaded", false, mgr.isLoaded());
    }

    static void testSeekTo() {
        TranslationAudioManager mgr = new TranslationAudioManager();
        mgr.loadAudio("https://example.com/audio.mp3");
        mgr.seekTo(5000);
        assertEquals("seeked position", 5000L, mgr.getCurrentPositionMs());
    }

    static void testSeekNegativeClampsToZero() {
        TranslationAudioManager mgr = new TranslationAudioManager();
        mgr.loadAudio("https://example.com/audio.mp3");
        mgr.seekTo(-100);
        assertEquals("negative seek clamped", 0L, mgr.getCurrentPositionMs());
    }

    static void testCannotPlayBeforeLoad() {
        TranslationAudioManager mgr = new TranslationAudioManager();
        try {
            mgr.play();
            fail("should throw on play before load");
        } catch (IllegalStateException e) {
            pass("play before load throws");
        }
    }

    static void testCannotPauseWhenNotPlaying() {
        TranslationAudioManager mgr = new TranslationAudioManager();
        mgr.loadAudio("https://example.com/audio.mp3");
        try {
            mgr.pause();
            fail("should throw on pause when READY");
        } catch (IllegalStateException e) {
            pass("pause when not playing throws");
        }
    }

    static void testCannotSeekWhenIdle() {
        TranslationAudioManager mgr = new TranslationAudioManager();
        try {
            mgr.seekTo(1000);
            fail("should throw on seek when IDLE");
        } catch (IllegalStateException e) {
            pass("seek when idle throws");
        }
    }

    static void testRelease() {
        TranslationAudioManager mgr = new TranslationAudioManager();
        mgr.loadAudio("https://example.com/audio.mp3");
        mgr.play();
        mgr.release();
        assertEquals("released", true, mgr.isReleased());
        assertEquals("state idle after release", State.IDLE, mgr.getState());
    }

    static void testCannotUseAfterRelease() {
        TranslationAudioManager mgr = new TranslationAudioManager();
        mgr.release();
        try {
            mgr.loadAudio("https://example.com/audio.mp3");
            fail("should throw after release");
        } catch (IllegalStateException e) {
            pass("load after release throws");
        }
        try {
            mgr.play();
            fail("should throw after release");
        } catch (IllegalStateException e) {
            pass("play after release throws");
        }
        try {
            mgr.stop();
            fail("should throw after release");
        } catch (IllegalStateException e) {
            pass("stop after release throws");
        }
    }

    static void testLoadNewAudioWhilePlaying() {
        TranslationAudioManager mgr = new TranslationAudioManager();
        mgr.loadAudio("https://example.com/audio1.mp3");
        mgr.play();
        mgr.loadAudio("https://example.com/audio2.mp3");
        assertEquals("state ready after reload", State.READY, mgr.getState());
        assertEquals("new url", "https://example.com/audio2.mp3", mgr.getCurrentAudioUrl());
    }

    static void testDuckVolumeClamping() {
        TranslationAudioManager mgr = new TranslationAudioManager();
        mgr.setDuckVolume(-0.5f);
        assertEquals("duck clamped min", 0f, mgr.getDuckVolume());
        mgr.setDuckVolume(1.5f);
        assertEquals("duck clamped max", 1f, mgr.getDuckVolume());
        mgr.setDuckVolume(0.5f);
        assertEquals("duck normal", 0.5f, mgr.getDuckVolume());
    }

    static void testTranslationVolumeClamping() {
        TranslationAudioManager mgr = new TranslationAudioManager();
        mgr.setTranslationVolume(-1f);
        assertEquals("trans vol clamped min", 0f, mgr.getTranslationVolume());
        mgr.setTranslationVolume(2f);
        assertEquals("trans vol clamped max", 1f, mgr.getTranslationVolume());
    }

    static void testLoadNullUrl() {
        TranslationAudioManager mgr = new TranslationAudioManager();
        mgr.loadAudio(null);
        assertEquals("error state on null url", State.ERROR, mgr.getState());
        mgr.loadAudio("");
        assertEquals("error state on empty url", State.ERROR, mgr.getState());
    }

    static void testStateListener() {
        TranslationAudioManager mgr = new TranslationAudioManager();
        final int[] callCount = {0};
        final State[] lastOld = {null};
        final State[] lastNew = {null};
        mgr.setStateListener(new TranslationAudioManager.StateListener() {
            public void onStateChanged(State oldState, State newState) {
                callCount[0]++;
                lastOld[0] = oldState;
                lastNew[0] = newState;
            }
            public void onError(String message) {}
        });
        mgr.loadAudio("https://example.com/audio.mp3");
        // IDLE->LOADING, LOADING->READY = 2 calls
        assertEquals("listener called", 2, callCount[0]);
        assertEquals("last transition to READY", State.READY, lastNew[0]);
    }

    static void testDoubleRelease() {
        TranslationAudioManager mgr = new TranslationAudioManager();
        mgr.release();
        mgr.release(); // should not throw
        pass("double release is safe");
    }

    static void testStopFromAnyState() {
        TranslationAudioManager mgr = new TranslationAudioManager();
        mgr.stop(); // from IDLE - should be fine
        assertEquals("stop from idle", State.IDLE, mgr.getState());

        mgr.loadAudio("https://example.com/audio.mp3");
        mgr.stop(); // from READY
        assertEquals("stop from ready", State.IDLE, mgr.getState());
    }

    // --- Assertion helpers ---

    static void assertEquals(String label, Object expected, Object actual) {
        if (expected == null ? actual == null : expected.equals(actual)) {
            pass(label);
        } else {
            fail(label + ": expected=" + expected + " actual=" + actual);
        }
    }

    static void pass(String label) {
        passed++;
        System.out.println("  ✓ " + label);
    }

    static void fail(String label) {
        failed++;
        System.out.println("  ✗ FAIL: " + label);
    }
}
