package mofo.com.pestscout.common.model;

/**
 * Synchronization state for entities that participate in edge ↔ cloud sync.
 */
public enum SyncStatus {
    /**
     * Exists only on the edge device/backend and has not been uploaded yet.
     */
    LOCAL_ONLY,

    /**
     * Metadata is present and upload is expected but not yet confirmed.
     */
    PENDING_UPLOAD,

    /**
     * Successfully synchronized with the cloud backend.
     */
    SYNCED,

    /**
     * Conflicting data was detected and requires resolution.
     */
    CONFLICT
}

