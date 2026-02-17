package app.revanced.integrations.youtube.vot.ui;

import app.revanced.integrations.youtube.vot.VotTranslationCoordinator;
import app.revanced.integrations.youtube.vot.settings.VotSettings;

/**
 * ReVanced patch entry point for injecting the VOT translation button
 * into YouTube's player controls overlay.
 *
 * Following ReVanced patch conventions:
 * - Static hook methods called from bytecode patches
 * - Minimal coupling to YouTube internals
 * - All VOT logic delegated to VotButtonController
 *
 * In a real ReVanced integration, the bytecode patch would:
 * 1. Find the player controls layout inflation
 * 2. Add a new ImageButton (translate icon) to the controls bar
 * 3. Call VotButtonPatch.initialize() with the view reference
 * 4. Call VotButtonPatch.onVideoChanged() from the video player hook
 *
 * The icon should be a translate/subtitle icon (similar to closed captions).
 * Visual states are managed via VotButtonController:
 *   - INACTIVE: gray tint (ColorFilter gray)
 *   - LOADING: pulsing animation (AlphaAnimation 0.3-1.0, repeat)
 *   - ACTIVE: highlighted tint (accent color, e.g. blue)
 */
public class VotButtonPatch {

    private static VotButtonController controller;
    private static boolean initialized = false;

    // Prevent instantiation
    private VotButtonPatch() {}

    /**
     * Initialize the VOT button patch. Called once during player setup.
     *
     * @param coordinator the translation coordinator instance
     * @param settings the VOT settings instance
     * @return the controller for further interaction
     */
    public static VotButtonController initialize(
            VotTranslationCoordinator coordinator,
            VotSettings settings) {
        controller = new VotButtonController(coordinator, settings);
        initialized = true;
        return controller;
    }

    /**
     * Hook: called from bytecode patch when player controls are created.
     * In a real patch, this receives the Android View and wires up click listeners.
     *
     * @param buttonView the button view adapter
     */
    public static void onPlayerControlsCreated(VotButtonController.ButtonView buttonView) {
        if (controller != null) {
            controller.setView(buttonView);
        }
    }

    /**
     * Hook: called when a new video starts playing.
     *
     * @param videoId the YouTube video ID
     */
    public static void onVideoChanged(String videoId) {
        if (controller != null) {
            controller.onVideoChanged(videoId);
        }
    }

    /**
     * Hook: called when the translation button is tapped.
     */
    public static void onButtonTapped() {
        if (controller != null) {
            controller.onButtonTapped();
        }
    }

    /**
     * Hook: called when VOT settings change.
     */
    public static void onSettingsChanged() {
        if (controller != null) {
            controller.onSettingsChanged();
        }
    }

    /** Check if the patch has been initialized. */
    public static boolean isInitialized() {
        return initialized;
    }

    /** Get the controller (for testing or advanced usage). */
    public static VotButtonController getController() {
        return controller;
    }

    /** Reset state (for testing). */
    public static void reset() {
        controller = null;
        initialized = false;
    }
}
