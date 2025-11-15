package mofo.com.pestscout.farm.model;

/**
 * Describes the primary production layout for a farm. This helps tailor default
 * bay/bench counts and guides UI rendering for greenhouse versus field setups.
 */
public enum FarmStructureType {
    GREENHOUSE,
    FIELD,
    OTHER
}
