package mofo.com.pestscout.scouting.repository;

import mofo.com.pestscout.scouting.model.CustomSpeciesDefinition;
import mofo.com.pestscout.scouting.model.ObservationCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomSpeciesDefinitionRepository extends JpaRepository<CustomSpeciesDefinition, UUID> {

    List<CustomSpeciesDefinition> findByFarmIdOrderByCategoryAscNameAsc(UUID farmId);

    List<CustomSpeciesDefinition> findByFarmIdAndCategoryOrderByNameAsc(UUID farmId, ObservationCategory category);

    List<CustomSpeciesDefinition> findByFarmIdAndIdIn(UUID farmId, Collection<UUID> ids);

    Optional<CustomSpeciesDefinition> findByFarmIdAndCategoryAndNormalizedName(UUID farmId,
                                                                               ObservationCategory category,
                                                                               String normalizedName);

    boolean existsByFarmIdAndCategoryAndCode(UUID farmId, ObservationCategory category, String code);

    Optional<CustomSpeciesDefinition> findByIdAndFarmId(UUID id, UUID farmId);
}
