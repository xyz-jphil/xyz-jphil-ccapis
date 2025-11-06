package xyz.jphil.ccapis.listener;

/**
 * Listener interface for usage tracking events.
 * Implementations can react to usage data updates (e.g., for ping service).
 */
public interface UsageListener {

    /**
     * Called when usage data is updated for a credential
     *
     * @param event the usage update event containing credential and usage information
     */
    void onUsageUpdate(UsageUpdateEvent event);

}
