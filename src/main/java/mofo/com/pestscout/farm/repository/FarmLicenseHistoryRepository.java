package mofo.com.pestscout.farm.repository;

import mofo.com.pestscout.farm.model.FarmLicenseHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FarmLicenseHistoryRepository extends JpaRepository<FarmLicenseHistory, UUID> {

    List<FarmLicenseHistory> findByFarmIdOrderByCreatedAtDesc(UUID farmId);

    Optional<FarmLicenseHistory> findFirstByFarmIdOrderByCreatedAtAsc(UUID farmId);

    boolean existsByFarmId(UUID farmId);
}
