package app.revanced.integrations.youtube.vot.ui;

/**
 * Visual states for the VOT translation button.
 */
public enum VotButtonState {
    /** Translation is off — button appears gray/inactive */
    INACTIVE,
    /** Translation is being requested or audio is loading — button is animated */
    LOADING,
    /** Translation audio is playing — button is highlighted/active */
    ACTIVE
}
