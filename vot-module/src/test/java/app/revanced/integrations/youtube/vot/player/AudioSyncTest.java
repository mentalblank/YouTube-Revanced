package app.revanced.integrations.youtube.vot.player;

import app.revanced.integrations.youtube.vot.player.TranslationAudioManager;
import app.revanced.integrations.youtube.vot.player.TranslationAudioManager.State;
import app.revanced.integrations.youtube.vot.player.AudioSyncController;

/**
 * Unit tests for audio synchronization logic (US-008).
 * Tests syncWithMainPlayer method and AudioSyncController.
 * Run: java AudioSyncTest
 */
public class AudioSyncTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        // syncWithMainPlayer tests
        testSyncPausesWhenMainPauses();
        testSyncResumesWhenMainResumes();
        testSyncResumesFromReady();
        testSyncSeeksWhenDriftExceeds500ms();
        testSyncNoSeekWhenDriftWithin500ms();
        testSyncExactThresholdNoSeek();
        testSyncJustOverThresholdSeeks();
        testSyncDoesNothingWhenNotLoaded();
        testSyncAfterMainSeekForward();
        testSyncAfterMainSeekBackward();
        testSyncCombinedPauseAndSeek();

        // AudioSyncController tests
        testControllerPeriodicSync();
        testControllerCountsSeeks();
        testControllerStartStop();
        testControllerNoSchedulerThrows();
        testControllerPauseResumeThroughSync();

        System.out.println("\n=== Audio Sync Tests: " + passed + " passed, " + failed + " failed ===");
        if (failed > 0) {
            System.exit(1);
        }
    }

    // --- syncWithMainPlayer tests ---

    static void testSyncPausesWhenMainPauses() {
        TranslationAudioManager mgr = new TranslationAudioManager();
        mgr.loadAudio("https://example.com/audio.mp3");
        mgr.play();
        assertEquals("playing before sync", State.PLAYING, mgr.getState());

        mgr.syncWithMainPlayer(0, false);
        assertEquals("paused after main paused", State.PAUSED, mgr.getState());
    }

    static void testSyncResumesWhenMainResumes() {
        TranslationAudioManager mgr = new TranslationAudioManager();
        mgr.loadAudio("https://example.com/audio.mp3");
        mgr.play();
        mgr.pause();
        assertEquals("paused before sync", State.PAUSED, mgr.getState());

        mgr.syncWithMainPlayer(0, true);
        assertEquals("playing after main resumed", State.PLAYING, mgr.getState());
    }

    static void testSyncResumesFromReady() {
        TranslationAudioManager mgr = new TranslationAudioManager();
        mgr.loadAudio("https://example.com/audio.mp3");
        assertEquals("ready state", State.READY, mgr.getState());

        mgr.syncWithMainPlayer(0, true);
        assertEquals("playing from ready", State.PLAYING, mgr.getState());
    }

    static void testSyncSeeksWhenDriftExceeds500ms() {
        TranslationAudioManager mgr = new TranslationAudioManager();
        mgr.loadAudio("https://example.com/audio.mp3");
        mgr.play();
        // Shadow at 0, main at 1000 → drift=1000 > 500
        boolean seeked = mgr.syncWithMainPlayer(1000, true);
        assertEquals("seek performed", true, seeked);
        assertEquals("position corrected", 1000L, mgr.getCurrentPositionMs());
    }

    static void testSyncNoSeekWhenDriftWithin500ms() {
        TranslationAudioManager mgr = new TranslationAudioManager();
        mgr.loadAudio("https://example.com/audio.mp3");
        mgr.play();
        mgr.seekTo(5000);
        // Shadow at 5000, main at 5300 → drift=300 < 500
        boolean seeked = mgr.syncWithMainPlayer(5300, true);
        assertEquals("no seek needed", false, seeked);
        assertEquals("position unchanged", 5000L, mgr.getCurrentPositionMs());
    }

    static void testSyncExactThresholdNoSeek() {
        TranslationAudioManager mgr = new TranslationAudioManager();
        mgr.loadAudio("https://example.com/audio.mp3");
        mgr.play();
        mgr.seekTo(1000);
        // drift = exactly 500 → not > 500, so no seek
        boolean seeked = mgr.syncWithMainPlayer(1500, true);
        assertEquals("no seek at exact threshold", false, seeked);
    }

    static void testSyncJustOverThresholdSeeks() {
        TranslationAudioManager mgr = new TranslationAudioManager();
        mgr.loadAudio("https://example.com/audio.mp3");
        mgr.play();
        mgr.seekTo(1000);
        // drift = 501 > 500
        boolean seeked = mgr.syncWithMainPlayer(1501, true);
        assertEquals("seek at 501ms drift", true, seeked);
        assertEquals("position corrected to 1501", 1501L, mgr.getCurrentPositionMs());
    }

    static void testSyncDoesNothingWhenNotLoaded() {
        TranslationAudioManager mgr = new TranslationAudioManager();
        boolean seeked = mgr.syncWithMainPlayer(5000, true);
        assertEquals("no seek when idle", false, seeked);
        assertEquals("still idle", State.IDLE, mgr.getState());
    }

    static void testSyncAfterMainSeekForward() {
        TranslationAudioManager mgr = new TranslationAudioManager();
        mgr.loadAudio("https://example.com/audio.mp3");
        mgr.play();
        mgr.seekTo(10000);
        // Main seeks to 30000 → drift = 20000
        boolean seeked = mgr.syncWithMainPlayer(30000, true);
        assertEquals("seek after main forward seek", true, seeked);
        assertEquals("position = 30000", 30000L, mgr.getCurrentPositionMs());
    }

    static void testSyncAfterMainSeekBackward() {
        TranslationAudioManager mgr = new TranslationAudioManager();
        mgr.loadAudio("https://example.com/audio.mp3");
        mgr.play();
        mgr.seekTo(30000);
        // Main seeks backward to 5000 → drift = 25000
        boolean seeked = mgr.syncWithMainPlayer(5000, true);
        assertEquals("seek after main backward seek", true, seeked);
        assertEquals("position = 5000", 5000L, mgr.getCurrentPositionMs());
    }

    static void testSyncCombinedPauseAndSeek() {
        TranslationAudioManager mgr = new TranslationAudioManager();
        mgr.loadAudio("https://example.com/audio.mp3");
        mgr.play();
        mgr.seekTo(1000);
        // Main paused at position 5000 → should pause AND seek
        boolean seeked = mgr.syncWithMainPlayer(5000, false);
        assertEquals("paused", State.PAUSED, mgr.getState());
        assertEquals("seeked too", true, seeked);
        assertEquals("position = 5000", 5000L, mgr.getCurrentPositionMs());
    }

    // --- AudioSyncController tests ---

    static void testControllerPeriodicSync() {
        TranslationAudioManager mgr = new TranslationAudioManager();
        mgr.loadAudio("https://example.com/audio.mp3");
        mgr.play();

        // Simulate main player at position 0, playing
        long[] mainPos = {0};
        boolean[] mainPlaying = {true};

        AudioSyncController controller = new AudioSyncController(mgr,
            new AudioSyncController.MainPlayerProvider() {
                public long getPositionMs() { return mainPos[0]; }
                public boolean isPlaying() { return mainPlaying[0]; }
            });

        // Use a manual scheduler
        Runnable[] scheduledTask = {null};
        controller.setScheduler(new AudioSyncController.Scheduler() {
            public void scheduleRepeating(Runnable task, long intervalMs) {
                scheduledTask[0] = task;
            }
            public void cancel() { scheduledTask[0] = null; }
        });

        controller.start();
        assertEquals("controller running", true, controller.isRunning());

        // Simulate 3 ticks with small drift
        mainPos[0] = 100;
        controller.performSync();
        mainPos[0] = 200;
        controller.performSync();
        mainPos[0] = 300;
        controller.performSync();

        assertEquals("3 sync ticks", 3, controller.getSyncCount());
        assertEquals("no seeks (drift < 500)", 0, controller.getSeekCount());
    }

    static void testControllerCountsSeeks() {
        TranslationAudioManager mgr = new TranslationAudioManager();
        mgr.loadAudio("https://example.com/audio.mp3");
        mgr.play();

        long[] mainPos = {0};

        AudioSyncController controller = new AudioSyncController(mgr,
            new AudioSyncController.MainPlayerProvider() {
                public long getPositionMs() { return mainPos[0]; }
                public boolean isPlaying() { return true; }
            });

        controller.setScheduler(new AudioSyncController.Scheduler() {
            public void scheduleRepeating(Runnable task, long intervalMs) {}
            public void cancel() {}
        });

        controller.start();

        // First tick: drift = 1000 > 500 → seek
        mainPos[0] = 1000;
        controller.performSync();
        assertEquals("1 seek after big drift", 1, controller.getSeekCount());

        // Second tick: drift = 0 → no seek
        controller.performSync();
        assertEquals("still 1 seek", 1, controller.getSeekCount());

        // Third tick: main jumps ahead by 2000
        mainPos[0] = 3000;
        controller.performSync();
        assertEquals("2 seeks total", 2, controller.getSeekCount());
        assertEquals("3 ticks total", 3, controller.getSyncCount());
    }

    static void testControllerStartStop() {
        TranslationAudioManager mgr = new TranslationAudioManager();
        mgr.loadAudio("https://example.com/audio.mp3");

        AudioSyncController controller = new AudioSyncController(mgr,
            new AudioSyncController.MainPlayerProvider() {
                public long getPositionMs() { return 0; }
                public boolean isPlaying() { return true; }
            });

        controller.setScheduler(new AudioSyncController.Scheduler() {
            public void scheduleRepeating(Runnable task, long intervalMs) {}
            public void cancel() {}
        });

        assertEquals("not running initially", false, controller.isRunning());
        controller.start();
        assertEquals("running after start", true, controller.isRunning());
        controller.stop();
        assertEquals("not running after stop", false, controller.isRunning());

        // performSync should not count after stop
        controller.performSync();
        assertEquals("no sync after stop", 0, controller.getSyncCount());
    }

    static void testControllerNoSchedulerThrows() {
        TranslationAudioManager mgr = new TranslationAudioManager();
        AudioSyncController controller = new AudioSyncController(mgr,
            new AudioSyncController.MainPlayerProvider() {
                public long getPositionMs() { return 0; }
                public boolean isPlaying() { return true; }
            });

        try {
            controller.start();
            fail("should throw without scheduler");
        } catch (IllegalStateException e) {
            pass("throws without scheduler");
        }
    }

    static void testControllerPauseResumeThroughSync() {
        TranslationAudioManager mgr = new TranslationAudioManager();
        mgr.loadAudio("https://example.com/audio.mp3");
        mgr.play();

        boolean[] mainPlaying = {true};

        AudioSyncController controller = new AudioSyncController(mgr,
            new AudioSyncController.MainPlayerProvider() {
                public long getPositionMs() { return 0; }
                public boolean isPlaying() { return mainPlaying[0]; }
            });

        controller.setScheduler(new AudioSyncController.Scheduler() {
            public void scheduleRepeating(Runnable task, long intervalMs) {}
            public void cancel() {}
        });

        controller.start();

        // Main pauses
        mainPlaying[0] = false;
        controller.performSync();
        assertEquals("shadow paused via controller", State.PAUSED, mgr.getState());

        // Main resumes
        mainPlaying[0] = true;
        controller.performSync();
        assertEquals("shadow resumed via controller", State.PLAYING, mgr.getState());
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
