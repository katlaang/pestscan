package mofo.com.pestscout.auth.dto;

/**
 * Simple statistics DTO for user counts in a farm.
 *
 * @param totalUsers    total number of users in the farm
 * @param activeUsers   number of enabled users
 * @param inactiveUsers number of disabled users
 */
public record UserStatsDto(
        long totalUsers,
        long activeUsers,
        long inactiveUsers
) {
}

