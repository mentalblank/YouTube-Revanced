package app.revanced.integrations.youtube.vot.ui;

import app.revanced.integrations.youtube.vot.VotTranslationCoordinator;
import app.revanced.integrations.youtube.vot.VotTranslationCoordinator.State;
import app.revanced.integrations.youtube.vot.settings.VotSettings;

/**
 * Controller for the VOT translation toggle button.
 * Maps coordinator states to button visual states and handles tap events.
 *
 * This is the pure-logic layer (no Android dependencies) so it can be unit tested.
 */
public class VotButtonController implements VotTranslationCoordinator.StateListener {

    /** Callback for UI updates — implemented by the actual Android view. */
    public interface ButtonView {
        void updateState(VotButtonState state);
        void setVisible(boolean visible);
        void showError(String message);
    }

    private final VotTranslationCoordinator coordinator;
    private final VotSettings settings;
    private ButtonView view;
    private VotButtonState currentState = VotButtonState.INACTIVE;
    private String currentVideoId;

    public VotButtonController(VotTranslationCoordinator coordinator, VotSettings settings) {
        this.coordinator = coordinator;
        this.settings = settings;
        this.coordinator.setStateListener(this);
    }

    public void setView(ButtonView view) {
        this.view = view;
        if (view != null) {
            view.updateState(currentState);
            view.setVisible(settings.isEnabled());
        }
    }

    /** Get the current visual state of the button. */
    public VotButtonState getCurrentState() {
        return currentState;
    }

    /**
     * Handle button tap. Toggles translation on/off.
     * If VOT is disabled in settings, does nothing.
     */
    public void onButtonTapped() {
        if (!settings.isEnabled()) {
            return;
        }

        State coordState = coordinator.getState();
        if (coordState == State.IDLE || coordState == State.ERROR) {
            // Start translation
            if (currentVideoId != null && !currentVideoId.isEmpty()) {
                coordinator.startTranslation(currentVideoId, settings.getTargetLanguage());
            }
        } else {
            // Stop translation
            coordinator.stopTranslation();
        }
    }

    /**
     * Called when the active video changes.
     */
    public void onVideoChanged(String videoId) {
        this.currentVideoId = videoId;
        coordinator.onVideoChanged(videoId);
    }

    /**
     * Called when VotSettings.isEnabled() changes.
     * Shows/hides the button accordingly.
     */
    public void onSettingsChanged() {
        if (view != null) {
            view.setVisible(settings.isEnabled());
        }
        // If VOT was just disabled, stop any active translation
        if (!settings.isEnabled() && coordinator.getState() != State.IDLE) {
            coordinator.stopTranslation();
        }
    }

    // --- StateListener implementation ---

    @Override
    public void onStateChanged(State oldState, State newState) {
        VotButtonState buttonState = mapState(newState);
        currentState = buttonState;
        if (view != null) {
            view.updateState(buttonState);
        }
    }

    @Override
    public void onError(String message) {
        currentState = VotButtonState.INACTIVE;
        if (view != null) {
            view.updateState(VotButtonState.INACTIVE);
            view.showError(message);
        }
    }

    /** Map coordinator state to button visual state. */
    static VotButtonState mapState(State coordinatorState) {
        switch (coordinatorState) {
            case REQUESTING:
            case LOADING:
                return VotButtonState.LOADING;
            case PLAYING:
                return VotButtonState.ACTIVE;
            case IDLE:
            case ERROR:
            default:
                return VotButtonState.INACTIVE;
        }
    }
}
