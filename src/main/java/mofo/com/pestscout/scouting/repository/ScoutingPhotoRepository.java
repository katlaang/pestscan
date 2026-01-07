package mofo.com.pestscout.scouting.repository;

import mofo.com.pestscout.common.model.SyncStatus;
import mofo.com.pestscout.scouting.model.ScoutingPhoto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ScoutingPhotoRepository extends JpaRepository<ScoutingPhoto, UUID> {

    Optional<ScoutingPhoto> findByLocalPhotoId(String localPhotoId);

    long countBySyncStatus(SyncStatus syncStatus);
}

