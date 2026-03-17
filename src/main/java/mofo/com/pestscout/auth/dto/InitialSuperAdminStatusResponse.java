package mofo.com.pestscout.auth.dto;

/**
 * Exposes whether the one-time initial super-admin bootstrap is still available.
 */
public record InitialSuperAdminStatusResponse(
        boolean superAdminExists,
        boolean bootstrapAllowed
) {
}
