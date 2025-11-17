package mofo.com.pestscout.farm.repository;

import mofo.com.pestscout.farm.model.Farm;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Data access for Farm entities.
 */
public interface FarmRepository extends JpaRepository<Farm, UUID> {

    /**
     * Find a farm by its name, ignoring letter case.
     * Used mainly for admin lookups and avoiding duplicate names.
     */
    Optional<Farm> findByNameIgnoreCase(String name);

    /**
     * Find a farm by its short human-readable tag (for example PS-0001).
     * Useful for URLs or quick search in the UI.
     */
    Optional<Farm> findByFarmTag(String farmTag);

    /**
     * Check if a farm tag is already in use.
     * Used when creating or editing farms to enforce unique tags.
     */
    boolean existsByFarmTag(String farmTag);
}
