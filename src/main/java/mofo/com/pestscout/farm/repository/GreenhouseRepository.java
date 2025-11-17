package mofo.com.pestscout.farm.repository;

import mofo.com.pestscout.farm.model.Greenhouse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GreenhouseRepository extends JpaRepository<Greenhouse, UUID> {

    List<Greenhouse> findByFarmId(UUID farmId);

    Optional<Greenhouse> findByIdAndFarmId(UUID greenhouseId, UUID farmId);

    boolean existsByFarmIdAndNameIgnoreCase(UUID farmId, String name);
}

