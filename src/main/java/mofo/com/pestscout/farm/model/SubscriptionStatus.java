package mofo.com.pestscout.farm.model;

/**
 * Subscription lifecycle for a farm.
 */
public enum SubscriptionStatus {

    /**
     * Farm is created but not yet fully activated.
     * This is the safe default to avoid giving access before billing or approval.
     */
    PENDING_ACTIVATION,

    /**
     * Subscription is active and features that require payment may be used.
     */
    ACTIVE,

    /**
     * Temporarily suspended, for example for non payment or manual lock.
     */
    SUSPENDED,

    /**
     * Subscription has been cancelled by the customer or admin.
     */
    CANCELLED,

    /**
     * Farm logically deleted. Kept for historical references and auditing.
     */
    DELETED
}
