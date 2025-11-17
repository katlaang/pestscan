package mofo.com.pestscout.farm.repository;

import mofo.com.pestscout.farm.model.FieldBlock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FieldBlockRepository extends JpaRepository<FieldBlock, UUID> {

    List<FieldBlock> findByFarmId(UUID farmId);

    Optional<FieldBlock> findByIdAndFarmId(UUID fieldBlockId, UUID farmId);

    boolean existsByFarmIdAndNameIgnoreCase(UUID farmId, String name);
}

