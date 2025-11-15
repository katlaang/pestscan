package mofo.com.pestscout.farm.model;

/**
 * Supported pest catalogue entries. Additional pests can be recorded by selecting OTHER and
 * providing notes within the observation payload.
 */
public enum PestType {
    THRIPS,
    RED_SPIDER_MITE,
    WHITEFLIES,
    MEALYBUGS,
    CATERPILLARS,
    FALSE_CODLING_MOTH,
    OTHER
}
