package mofo.com.pestscout.scouting.repository;

import mofo.com.pestscout.scouting.model.PhotoAnalysisReviewStatus;
import mofo.com.pestscout.scouting.model.ScoutingPhotoAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScoutingPhotoAnalysisRepository extends JpaRepository<ScoutingPhotoAnalysis, UUID> {

    Optional<ScoutingPhotoAnalysis> findByPhoto_Id(UUID photoId);

    List<ScoutingPhotoAnalysis> findByFarmId(UUID farmId);

    List<ScoutingPhotoAnalysis> findByFarmIdAndReviewStatusIn(UUID farmId, Collection<PhotoAnalysisReviewStatus> reviewStatuses);
}
