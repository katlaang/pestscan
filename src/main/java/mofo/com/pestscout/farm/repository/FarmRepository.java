package mofo.com.pestscout.farm.repository;

import mofo.com.pestscout.farm.model.Farm;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FarmRepository extends JpaRepository<Farm, UUID> {

    Optional<Farm> findByNameIgnoreCase(String name);
}
