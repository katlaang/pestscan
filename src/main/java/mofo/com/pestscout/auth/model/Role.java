package mofo.com.pestscout.auth.model;


/**
 * User roles in the system
 * Defines hierarchical access levels
 */
public enum Role {
    /**
     * Scout - Can execute assigned scouting sessions and record observations.
     * Cannot create or administer sessions.
     */
    SCOUT,

    /**
     * Manager - Can view all data, create treatments, manage alerts
     * Can assign scouts and oversee operations
     */
    MANAGER,

    /**
     * Farm Admin - Full control over farm data and users
     * Can manage sites, users, and settings for their farm
     */
    FARM_ADMIN,

    /**
     * Super Admin - System administrator
     * Can manage multiple farms, billing, and system settings
     */
    SUPER_ADMIN,

    /**
     * Edge Sync - Headless service role for edge-to-cloud sync
     * Scoped to sync endpoints only (no interactive permissions).
     */
    EDGE_SYNC;

    /**
     * Check if this role has admin privileges
     */
    public boolean isAdmin() {
        return this == FARM_ADMIN || this == SUPER_ADMIN;
    }

    /**
     * Check if this role can manage users
     */
    public boolean canManageUsers() {
        return this == FARM_ADMIN || this == SUPER_ADMIN;
    }

    /**
     * Check if this role can view reports
     */
    public boolean canViewReports() {
        return this != SCOUT && this != EDGE_SYNC;
    }
}
