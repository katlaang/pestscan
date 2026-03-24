package mofo.com.pestscout.optional.repository;

import mofo.com.pestscout.optional.model.SupplyOrderRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SupplyOrderRequestRepository extends JpaRepository<SupplyOrderRequest, UUID> {

    List<SupplyOrderRequest> findByFarmIdOrderByCreatedAtDesc(UUID farmId);

    boolean existsByFarmId(UUID farmId);
}
