package app.revanced.integrations.youtube.vot.ui;

import app.revanced.integrations.youtube.vot.VotTranslationCoordinator;
import app.revanced.integrations.youtube.vot.VotTranslationCoordinator.State;
import app.revanced.integrations.youtube.vot.api.YandexTranslationClient;
import app.revanced.integrations.youtube.vot.player.AudioDuckingManager;
import app.revanced.integrations.youtube.vot.player.AudioSyncController;
import app.revanced.integrations.youtube.vot.player.TranslationAudioManager;
import app.revanced.integrations.youtube.vot.settings.VotSettings;

import java.util.ArrayList;
import java.util.List;

/**
 * Unit tests for VotButtonController and VotButtonPatch (US-012).
 * Run: javac + java VotButtonControllerTest
 */
public class VotButtonControllerTest {

    private static int passed = 0;
    private static int failed = 0;

    static void assertEquals(Object expected, Object actual, String test) {
        if (expected == null ? actual == null : expected.equals(actual)) {
            passed++;
            System.out.println("  ✅ " + test);
        } else {
            failed++;
            System.out.println("  ❌ " + test + " (expected=" + expected + ", actual=" + actual + ")");
        }
    }

    static void assertTrue(boolean condition, String test) {
        assertEquals(true, condition, test);
    }

    static void assertFalse(boolean condition, String test) {
        assertEquals(false, condition, test);
    }

    // --- Fakes ---

    static class FakeButtonView implements VotButtonController.ButtonView {
        VotButtonState lastState;
        Boolean lastVisible;
        String lastError;
        List<VotButtonState> stateHistory = new ArrayList<>();

        @Override
        public void updateState(VotButtonState state) {
            lastState = state;
            stateHistory.add(state);
        }

        @Override
        public void setVisible(boolean visible) {
            lastVisible = visible;
        }

        @Override
        public void showError(String message) {
            lastError = message;
        }
    }

    static VotTranslationCoordinator createCoordinator() {
        YandexTranslationClient client = new YandexTranslationClient();
        TranslationAudioManager audioMgr = new TranslationAudioManager();
        AudioDuckingManager duckMgr = new AudioDuckingManager();
        AudioSyncController syncCtrl = new AudioSyncController(
            audioMgr,
            new AudioSyncController.MainPlayerProvider() {
                public long getPositionMs() { return 0; }
                public boolean isPlaying() { return false; }
            }
        );
        return new VotTranslationCoordinator(
            client, audioMgr, duckMgr, syncCtrl,
            Runnable::run, Runnable::run
        );
    }

    // --- Tests ---

    static void testInitialState() {
        System.out.println("\n--- Initial State ---");
        VotTranslationCoordinator coord = createCoordinator();
        VotSettings settings = new VotSettings();
        VotButtonController ctrl = new VotButtonController(coord, settings);

        assertEquals(VotButtonState.INACTIVE, ctrl.getCurrentState(), "Initial state is INACTIVE");
    }

    static void testMapStateIdle() {
        System.out.println("\n--- State Mapping ---");
        assertEquals(VotButtonState.INACTIVE, VotButtonController.mapState(State.IDLE), "IDLE -> INACTIVE");
        assertEquals(VotButtonState.LOADING, VotButtonController.mapState(State.REQUESTING), "REQUESTING -> LOADING");
        assertEquals(VotButtonState.LOADING, VotButtonController.mapState(State.LOADING), "LOADING -> LOADING");
        assertEquals(VotButtonState.ACTIVE, VotButtonController.mapState(State.PLAYING), "PLAYING -> ACTIVE");
        assertEquals(VotButtonState.INACTIVE, VotButtonController.mapState(State.ERROR), "ERROR -> INACTIVE");
    }

    static void testSetViewUpdatesState() {
        System.out.println("\n--- setView updates state ---");
        VotTranslationCoordinator coord = createCoordinator();
        VotSettings settings = new VotSettings();
        VotButtonController ctrl = new VotButtonController(coord, settings);
        FakeButtonView view = new FakeButtonView();

        ctrl.setView(view);
        assertEquals(VotButtonState.INACTIVE, view.lastState, "View receives initial INACTIVE state");
    }

    static void testSetViewRespectsEnabled() {
        System.out.println("\n--- setView visibility respects settings ---");
        VotTranslationCoordinator coord = createCoordinator();
        VotSettings settings = new VotSettings();
        settings.setEnabled(false);

        VotButtonController ctrl = new VotButtonController(coord, settings);
        FakeButtonView view = new FakeButtonView();
        ctrl.setView(view);
        assertEquals(false, view.lastVisible, "View hidden when VOT disabled");

        VotSettings settings2 = new VotSettings();
        settings2.setEnabled(true);
        VotButtonController ctrl2 = new VotButtonController(createCoordinator(), settings2);
        FakeButtonView view2 = new FakeButtonView();
        ctrl2.setView(view2);
        assertEquals(true, view2.lastVisible, "View visible when VOT enabled");
    }

    static void testTapDoesNothingWhenDisabled() {
        System.out.println("\n--- Tap does nothing when disabled ---");
        VotTranslationCoordinator coord = createCoordinator();
        VotSettings settings = new VotSettings();
        settings.setEnabled(false);
        VotButtonController ctrl = new VotButtonController(coord, settings);
        ctrl.onVideoChanged("test123");

        ctrl.onButtonTapped(); // Should not throw or start translation
        assertEquals(State.IDLE, coord.getState(), "Coordinator stays IDLE when tapped with VOT disabled");
    }

    static void testOnStateChangedUpdatesView() {
        System.out.println("\n--- onStateChanged updates view ---");
        VotTranslationCoordinator coord = createCoordinator();
        VotSettings settings = new VotSettings();
        VotButtonController ctrl = new VotButtonController(coord, settings);
        FakeButtonView view = new FakeButtonView();
        ctrl.setView(view);

        // Simulate state change callback
        ctrl.onStateChanged(State.IDLE, State.REQUESTING);
        assertEquals(VotButtonState.LOADING, view.lastState, "View updated to LOADING on REQUESTING");
        assertEquals(VotButtonState.LOADING, ctrl.getCurrentState(), "Controller state is LOADING");

        ctrl.onStateChanged(State.REQUESTING, State.PLAYING);
        assertEquals(VotButtonState.ACTIVE, view.lastState, "View updated to ACTIVE on PLAYING");
    }

    static void testOnErrorUpdatesView() {
        System.out.println("\n--- onError updates view ---");
        VotTranslationCoordinator coord = createCoordinator();
        VotSettings settings = new VotSettings();
        VotButtonController ctrl = new VotButtonController(coord, settings);
        FakeButtonView view = new FakeButtonView();
        ctrl.setView(view);

        ctrl.onError("Test error");
        assertEquals(VotButtonState.INACTIVE, view.lastState, "View set to INACTIVE on error");
        assertEquals("Test error", view.lastError, "Error message passed to view");
    }

    static void testOnSettingsChangedHidesButton() {
        System.out.println("\n--- onSettingsChanged hides button ---");
        VotTranslationCoordinator coord = createCoordinator();
        VotSettings settings = new VotSettings();
        settings.setEnabled(true);
        VotButtonController ctrl = new VotButtonController(coord, settings);
        FakeButtonView view = new FakeButtonView();
        ctrl.setView(view);

        settings.setEnabled(false);
        ctrl.onSettingsChanged();
        assertEquals(false, view.lastVisible, "Button hidden when VOT disabled");
    }

    static void testVotButtonPatchLifecycle() {
        System.out.println("\n--- VotButtonPatch lifecycle ---");
        VotButtonPatch.reset();
        assertFalse(VotButtonPatch.isInitialized(), "Not initialized initially");

        VotTranslationCoordinator coord = createCoordinator();
        VotSettings settings = new VotSettings();
        settings.setEnabled(true);

        VotButtonController ctrl = VotButtonPatch.initialize(coord, settings);
        assertTrue(VotButtonPatch.isInitialized(), "Initialized after initialize()");
        assertEquals(ctrl, VotButtonPatch.getController(), "getController returns correct instance");

        FakeButtonView view = new FakeButtonView();
        VotButtonPatch.onPlayerControlsCreated(view);
        assertEquals(VotButtonState.INACTIVE, view.lastState, "View receives state after onPlayerControlsCreated");

        VotButtonPatch.onVideoChanged("abc123");
        // Should not crash

        VotButtonPatch.onButtonTapped();
        // With enabled=true and videoId set, this triggers startTranslation

        VotButtonPatch.onSettingsChanged();
        // Should not crash

        VotButtonPatch.reset();
        assertFalse(VotButtonPatch.isInitialized(), "Reset clears initialization");
    }

    public static void main(String[] args) {
        System.out.println("=== VotButtonController & VotButtonPatch Tests (US-012) ===");

        testInitialState();
        testMapStateIdle();
        testSetViewUpdatesState();
        testSetViewRespectsEnabled();
        testTapDoesNothingWhenDisabled();
        testOnStateChangedUpdatesView();
        testOnErrorUpdatesView();
        testOnSettingsChangedHidesButton();
        testVotButtonPatchLifecycle();

        System.out.println("\n=== Results: " + passed + " passed, " + failed + " failed ===");
        System.exit(failed > 0 ? 1 : 0);
    }
}
