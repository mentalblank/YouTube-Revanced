package app.revanced.integrations.youtube.vot.player;

/**
 * Unit tests for AudioDuckingManager (US-009).
 * Run: java AudioDuckingManagerTest
 */
public class AudioDuckingManagerTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        testDefaultDuckVolume();
        testStartDuckingWithoutSchedulerSnaps();
        testStopDuckingRestoresVolume();
        testSetDuckVolumeConfigurable();
        testSetDuckVolumeWhileDucking();
        testSetDuckVolumeClampsRange();
        testIsDuckingState();
        testFadeStepsGraduallyChange();
        testFadeStepsReachTarget();
        testFadeStepsFromDuckToFull();
        testVolumeApplierCalled();
        testSetVolumeImmediate();
        testStartDuckingWithScheduler();
        testMultipleStartStopCycles();
        testDefaultFadeDuration();

        System.out.println("\n=== AudioDuckingManager Tests: " + passed + " passed, " + failed + " failed ===");
        if (failed > 0) {
            System.exit(1);
        }
    }

    static void testDefaultDuckVolume() {
        AudioDuckingManager mgr = new AudioDuckingManager();
        assertFloatEquals("default duck volume is 0.15", 0.15f, mgr.getDuckVolume());
        assertFloatEquals("initial volume is 1.0", 1.0f, mgr.getCurrentVolume());
    }

    static void testStartDuckingWithoutSchedulerSnaps() {
        AudioDuckingManager mgr = new AudioDuckingManager();
        mgr.startDucking();
        assertFloatEquals("volume snaps to duck level", 0.15f, mgr.getCurrentVolume());
        assertEquals("is ducking", true, mgr.isDucking());
    }

    static void testStopDuckingRestoresVolume() {
        AudioDuckingManager mgr = new AudioDuckingManager();
        mgr.startDucking();
        mgr.stopDucking();
        assertFloatEquals("volume restored to 1.0", 1.0f, mgr.getCurrentVolume());
        assertEquals("not ducking", false, mgr.isDucking());
    }

    static void testSetDuckVolumeConfigurable() {
        AudioDuckingManager mgr = new AudioDuckingManager();
        mgr.setDuckVolume(0.3f);
        assertFloatEquals("duck volume set to 0.3", 0.3f, mgr.getDuckVolume());
        mgr.startDucking();
        assertFloatEquals("volume at 0.3", 0.3f, mgr.getCurrentVolume());
    }

    static void testSetDuckVolumeWhileDucking() {
        AudioDuckingManager mgr = new AudioDuckingManager();
        mgr.startDucking();
        assertFloatEquals("at default 0.15", 0.15f, mgr.getCurrentVolume());
        mgr.setDuckVolume(0.25f);
        assertFloatEquals("adjusted to 0.25", 0.25f, mgr.getCurrentVolume());
    }

    static void testSetDuckVolumeClampsRange() {
        AudioDuckingManager mgr = new AudioDuckingManager();
        mgr.setDuckVolume(-0.5f);
        assertFloatEquals("clamped to 0", 0f, mgr.getDuckVolume());
        mgr.setDuckVolume(2.0f);
        assertFloatEquals("clamped to 1", 1.0f, mgr.getDuckVolume());
    }

    static void testIsDuckingState() {
        AudioDuckingManager mgr = new AudioDuckingManager();
        assertEquals("not ducking initially", false, mgr.isDucking());
        mgr.startDucking();
        assertEquals("ducking after start", true, mgr.isDucking());
        mgr.stopDucking();
        assertEquals("not ducking after stop", false, mgr.isDucking());
    }

    static void testFadeStepsGraduallyChange() {
        AudioDuckingManager mgr = new AudioDuckingManager();
        mgr.setFadeSteps(5);
        // Manually set target by starting ducking without scheduler
        // We'll test executeFadeStep directly
        mgr.startDucking(); // snaps to 0.15 without scheduler
        // Reset to test fade
        mgr.setVolumeImmediate(1.0f);
        // Manually set up for fade
        mgr.startDucking(); // snaps again
        // So let's test fade steps in isolation:
        AudioDuckingManager mgr2 = new AudioDuckingManager();
        mgr2.setFadeSteps(4);
        // Simulate: currentVolume=1.0, targetVolume will be 0.15 but we need scheduler
        // Use executeFadeStep directly after setting up state
        // Without scheduler, startDucking snaps. We need to test fade steps manually.
        
        // Test: volume at 1.0, execute fade steps towards 0.0 target
        AudioDuckingManager mgr3 = new AudioDuckingManager();
        mgr3.setDuckVolume(0.0f);
        mgr3.setFadeSteps(10);
        // Set a no-op scheduler so it doesn't snap
        mgr3.setFadeScheduler(new AudioDuckingManager.FadeScheduler() {
            public void scheduleStep(Runnable step, long delayMs) { /* don't execute */ }
            public void cancelAll() {}
        });
        mgr3.startDucking();
        // Volume should still be 1.0 since scheduler doesn't execute
        assertFloatEquals("volume still 1.0 with lazy scheduler", 1.0f, mgr3.getCurrentVolume());

        // Manually step
        boolean more = mgr3.executeFadeStep();
        assertEquals("more steps needed", true, more);
        float afterStep = mgr3.getCurrentVolume();
        assertEquals("volume decreased", true, afterStep < 1.0f);
        pass("fade step gradually changes volume");
    }

    static void testFadeStepsReachTarget() {
        AudioDuckingManager mgr = new AudioDuckingManager();
        mgr.setDuckVolume(0.2f);
        mgr.setFadeSteps(5);
        mgr.setFadeScheduler(new AudioDuckingManager.FadeScheduler() {
            public void scheduleStep(Runnable step, long delayMs) {}
            public void cancelAll() {}
        });
        mgr.startDucking();

        // Execute many steps to ensure convergence
        for (int i = 0; i < 50; i++) {
            if (!mgr.executeFadeStep()) break;
        }
        assertFloatEquals("reached target 0.2", 0.2f, mgr.getCurrentVolume());
    }

    static void testFadeStepsFromDuckToFull() {
        AudioDuckingManager mgr = new AudioDuckingManager();
        mgr.setFadeSteps(5);
        mgr.startDucking(); // snaps to 0.15
        assertFloatEquals("at duck level", 0.15f, mgr.getCurrentVolume());

        mgr.setFadeScheduler(new AudioDuckingManager.FadeScheduler() {
            public void scheduleStep(Runnable step, long delayMs) {}
            public void cancelAll() {}
        });
        mgr.stopDucking();

        for (int i = 0; i < 50; i++) {
            if (!mgr.executeFadeStep()) break;
        }
        assertFloatEquals("restored to 1.0", 1.0f, mgr.getCurrentVolume());
    }

    static void testVolumeApplierCalled() {
        AudioDuckingManager mgr = new AudioDuckingManager();
        float[] appliedVolume = {-1f};
        int[] callCount = {0};
        mgr.setVolumeApplier(v -> { appliedVolume[0] = v; callCount[0]++; });

        mgr.startDucking();
        assertFloatEquals("applier got duck volume", 0.15f, appliedVolume[0]);
        assertEquals("applier was called", true, callCount[0] > 0);

        mgr.stopDucking();
        assertFloatEquals("applier got full volume", 1.0f, appliedVolume[0]);
    }

    static void testSetVolumeImmediate() {
        AudioDuckingManager mgr = new AudioDuckingManager();
        mgr.setVolumeImmediate(0.5f);
        assertFloatEquals("volume set to 0.5", 0.5f, mgr.getCurrentVolume());
        assertFloatEquals("target also 0.5", 0.5f, mgr.getTargetVolume());
    }

    static void testStartDuckingWithScheduler() {
        AudioDuckingManager mgr = new AudioDuckingManager();
        mgr.setFadeSteps(30);
        java.util.List<Runnable> tasks = new java.util.ArrayList<>();
        boolean[] cancelled = {false};
        mgr.setFadeScheduler(new AudioDuckingManager.FadeScheduler() {
            public void scheduleStep(Runnable step, long delayMs) { if (!cancelled[0]) tasks.add(step); }
            public void cancelAll() { cancelled[0] = true; tasks.clear(); cancelled[0] = false; }
        });

        mgr.startDucking();
        assertEquals("tasks scheduled", true, tasks.size() > 0);
        // Execute all scheduled tasks iteratively
        int iterations = 0;
        while (!tasks.isEmpty() && iterations < 100) {
            Runnable t = tasks.remove(0);
            t.run();
            iterations++;
        }
        // Volume should be at or near duck level
        assertEquals("volume near duck level", true, mgr.getCurrentVolume() <= 0.2f);
    }

    static void testMultipleStartStopCycles() {
        AudioDuckingManager mgr = new AudioDuckingManager();
        for (int i = 0; i < 5; i++) {
            mgr.startDucking();
            assertFloatEquals("ducked cycle " + i, 0.15f, mgr.getCurrentVolume());
            mgr.stopDucking();
            assertFloatEquals("restored cycle " + i, 1.0f, mgr.getCurrentVolume());
        }
        pass("5 start/stop cycles completed");
    }

    static void testDefaultFadeDuration() {
        AudioDuckingManager mgr = new AudioDuckingManager();
        // Just verify constants are accessible
        assertEquals("default fade 300ms", 300L, AudioDuckingManager.DEFAULT_FADE_DURATION_MS);
        assertEquals("default steps 10", 10, AudioDuckingManager.DEFAULT_FADE_STEPS);
        assertFloatEquals("default duck 0.15", 0.15f, AudioDuckingManager.DEFAULT_DUCK_VOLUME);
        pass("constants verified");
    }

    // --- Helpers ---

    static void assertFloatEquals(String label, float expected, float actual) {
        if (Math.abs(expected - actual) < 0.01f) {
            pass(label);
        } else {
            fail(label + ": expected=" + expected + " actual=" + actual);
        }
    }

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
