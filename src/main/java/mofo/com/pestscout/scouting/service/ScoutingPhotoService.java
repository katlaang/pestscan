package mofo.com.pestscout.scouting.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mofo.com.pestscout.auth.model.Role;
import mofo.com.pestscout.common.exception.BadRequestException;
import mofo.com.pestscout.common.exception.ForbiddenException;
import mofo.com.pestscout.common.exception.ResourceNotFoundException;
import mofo.com.pestscout.common.model.SyncStatus;
import mofo.com.pestscout.farm.security.CurrentUserService;
import mofo.com.pestscout.farm.security.FarmAccessService;
import mofo.com.pestscout.scouting.dto.PhotoMetadataRequest;
import mofo.com.pestscout.scouting.dto.PhotoUploadConfirmationRequest;
import mofo.com.pestscout.scouting.dto.ScoutingPhotoDto;
import mofo.com.pestscout.scouting.model.ScoutingObservation;
import mofo.com.pestscout.scouting.model.ScoutingPhoto;
import mofo.com.pestscout.scouting.model.ScoutingSession;
import mofo.com.pestscout.scouting.model.SessionStatus;
import mofo.com.pestscout.scouting.repository.ScoutingObservationRepository;
import mofo.com.pestscout.scouting.repository.ScoutingPhotoRepository;
import mofo.com.pestscout.scouting.repository.ScoutingSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScoutingPhotoService {

    private final ScoutingSessionRepository sessionRepository;
    private final ScoutingObservationRepository observationRepository;
    private final ScoutingPhotoRepository photoRepository;
    private final FarmAccessService farmAccessService;
    private final CurrentUserService currentUserService;

    @Transactional
    public ScoutingPhotoDto registerMetadata(PhotoMetadataRequest request) {
        ScoutingSession session = sessionRepository.findById(request.sessionId())
                .orElseThrow(() -> new ResourceNotFoundException("ScoutingSession", "id", request.sessionId()));

        farmAccessService.requireViewAccess(session.getFarm());

        ScoutingObservation observation = null;
        if (request.observationId() != null) {
            observation = observationRepository.findById(request.observationId())
                    .orElseThrow(() -> new ResourceNotFoundException("ScoutingObservation", "id", request.observationId()));
            if (!observation.getSession().getId().equals(session.getId())) {
                throw new BadRequestException("Observation does not belong to the session");
            }
        }

        Optional<ScoutingPhoto> existing = photoRepository.findByLocalPhotoId(request.localPhotoId());
        if (existing.isPresent()) {
            ScoutingPhoto photo = existing.get();
            enforceSameSession(session, photo);
            return toDto(photo);
        }

        ScoutingPhoto photo = ScoutingPhoto.builder()
                .session(session)
                .observation(observation)
                .farmId(session.getFarm().getId())
                .localPhotoId(request.localPhotoId())
                .purpose(request.purpose())
                .capturedAt(request.capturedAt())
                .build();

        photo.setSyncStatus(SyncStatus.PENDING_UPLOAD);

        ScoutingPhoto saved = photoRepository.save(photo);
        log.info("Registered photo metadata {} for session {} by user {}", saved.getId(), session.getId(), currentUserService.getCurrentUserId());
        return toDto(saved);
    }

    @Transactional
    public ScoutingPhotoDto confirmUpload(PhotoUploadConfirmationRequest request) {
        ScoutingPhoto photo = photoRepository.findByLocalPhotoId(request.localPhotoId())
                .orElseThrow(() -> new ResourceNotFoundException("ScoutingPhoto", "localPhotoId", request.localPhotoId()));

        enforceSameSession(request.sessionId(), photo);
        farmAccessService.requireViewAccess(photo.getSession().getFarm());

        if (photo.getSession().getStatus() == SessionStatus.COMPLETED
                && currentUserService.getCurrentUser().getRole() == Role.SCOUT) {
            throw new ForbiddenException("Scouts cannot confirm photo uploads for completed sessions.");
        }

        photo.setObjectKey(request.objectKey());
        photo.setSyncStatus(SyncStatus.SYNCED);

        ScoutingPhoto saved = photoRepository.save(photo);
        log.info("Confirmed upload for photo {} (session {})", saved.getId(), saved.getSession().getId());
        return toDto(saved);
    }

    private void enforceSameSession(ScoutingSession session, ScoutingPhoto photo) {
        enforceSameSession(session.getId(), photo);
    }

    private void enforceSameSession(java.util.UUID sessionId, ScoutingPhoto photo) {
        if (!photo.getSession().getId().equals(sessionId)) {
            throw new BadRequestException("Photo belongs to a different session");
        }
    }

    private ScoutingPhotoDto toDto(ScoutingPhoto photo) {
        return new ScoutingPhotoDto(
                photo.getId(),
                photo.getSession().getId(),
                photo.getObservation() != null ? photo.getObservation().getId() : null,
                photo.getFarmId(),
                photo.getLocalPhotoId(),
                photo.getPurpose(),
                photo.getObjectKey(),
                photo.getCapturedAt(),
                photo.getUpdatedAt(),
                photo.getSyncStatus()
        );
    }
}
