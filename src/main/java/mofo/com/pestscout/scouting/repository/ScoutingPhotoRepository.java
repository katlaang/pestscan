package mofo.com.pestscout.scouting.repository;

import mofo.com.pestscout.common.model.SyncStatus;
import mofo.com.pestscout.scouting.model.ScoutingPhoto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScoutingPhotoRepository extends JpaRepository<ScoutingPhoto, UUID> {

    Optional<ScoutingPhoto> findByLocalPhotoId(String localPhotoId);

    Optional<ScoutingPhoto> findByLocalPhotoIdAndDeletedFalse(String localPhotoId);

    List<ScoutingPhoto> findBySessionId(UUID sessionId);

    List<ScoutingPhoto> findBySessionIdAndDeletedFalseOrderByCapturedAtAscCreatedAtAsc(UUID sessionId);

    Optional<ScoutingPhoto> findByIdAndSessionId(UUID photoId, UUID sessionId);

    List<ScoutingPhoto> findByFarmId(UUID farmId);

    long countBySessionIdAndSessionTarget_IdAndBayIndexAndBenchIndexAndSpotIndexAndDeletedFalse(
            UUID sessionId,
            UUID sessionTargetId,
            Integer bayIndex,
            Integer benchIndex,
            Integer spotIndex
    );

    long countBySyncStatus(SyncStatus syncStatus);
}

