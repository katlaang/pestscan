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
import mofo.com.pestscout.scouting.model.*;
import mofo.com.pestscout.scouting.repository.ScoutingObservationRepository;
import mofo.com.pestscout.scouting.repository.ScoutingPhotoRepository;
import mofo.com.pestscout.scouting.repository.ScoutingSessionRepository;
import mofo.com.pestscout.scouting.repository.ScoutingSessionTargetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScoutingPhotoService {

    private static final int MAX_ACTIVE_PHOTOS_PER_CELL = 5;

    private final ScoutingSessionRepository sessionRepository;
    private final ScoutingObservationRepository observationRepository;
    private final ScoutingPhotoRepository photoRepository;
    private final ScoutingSessionTargetRepository sessionTargetRepository;
    private final FarmAccessService farmAccessService;
    private final CurrentUserService currentUserService;

    @Transactional
    public ScoutingPhotoDto registerMetadata(PhotoMetadataRequest request) {
        ScoutingSession session = sessionRepository.findById(request.sessionId())
                .orElseThrow(() -> new ResourceNotFoundException("ScoutingSession", "id", request.sessionId()));

        enforceAssignedScout(session);
        ensureSessionAllowsPhotoChanges(session);

        CellPhotoContext context = null;
        ScoutingObservation observation = null;
        if (request.observationId() != null) {
            observation = observationRepository.findById(request.observationId())
                    .orElseThrow(() -> new ResourceNotFoundException("ScoutingObservation", "id", request.observationId()));
            if (!observation.getSession().getId().equals(session.getId())) {
                throw new BadRequestException("Observation does not belong to the session");
            }
            context = CellPhotoContext.fromObservation(observation);
        }

        if (context == null) {
            context = resolveContextFromRequest(session, request);
        }

        assertPhotoLimitNotExceeded(session.getId(), request.localPhotoId(), context);

        Optional<ScoutingPhoto> existing = photoRepository.findByLocalPhotoIdAndDeletedFalse(request.localPhotoId());
        if (existing.isPresent()) {
            ScoutingPhoto photo = existing.get();
            enforceSameSession(session, photo);
            return toDto(photo);
        }

        ScoutingPhoto photo = ScoutingPhoto.builder()
                .session(session)
                .observation(observation)
                .sessionTarget(context.sessionTarget())
                .farmId(session.getFarm().getId())
                .bayIndex(context.bayIndex())
                .bayLabel(context.bayTag())
                .benchIndex(context.benchIndex())
                .benchLabel(context.benchTag())
                .spotIndex(context.spotIndex())
                .localPhotoId(request.localPhotoId())
                .purpose(request.purpose())
                .sourceType(resolvePhotoSourceType(request.sourceType(), session))
                .capturedAt(request.capturedAt())
                .build();

        photo.setSyncStatus(SyncStatus.PENDING_UPLOAD);

        ScoutingPhoto saved = photoRepository.save(photo);
        log.info("Registered photo metadata {} for session {} by user {}", saved.getId(), session.getId(), currentUserService.getCurrentUserId());
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public List<ScoutingPhotoDto> listSessionPhotos(UUID sessionId) {
        return photoRepository.findBySessionIdAndDeletedFalseOrderByCapturedAtAscCreatedAtAsc(sessionId).stream()
                .sorted(Comparator
                        .comparing(ScoutingPhoto::getCapturedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(ScoutingPhoto::getCreatedAt))
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public ScoutingPhotoDto confirmUpload(PhotoUploadConfirmationRequest request) {
        ScoutingPhoto photo = photoRepository.findByLocalPhotoIdAndDeletedFalse(request.localPhotoId())
                .orElseThrow(() -> new ResourceNotFoundException("ScoutingPhoto", "localPhotoId", request.localPhotoId()));

        enforceSameSession(request.sessionId(), photo);
        enforceAssignedScout(photo.getSession());
        ensureSessionAllowsPhotoChanges(photo.getSession());

        if (photo.getSession().getStatus() == SessionStatus.COMPLETED) {
            throw new ForbiddenException("Scouts cannot confirm photo uploads for completed sessions.");
        }

        photo.setObjectKey(request.objectKey());
        photo.setSyncStatus(SyncStatus.SYNCED);

        ScoutingPhoto saved = photoRepository.save(photo);
        log.info("Confirmed upload for photo {} (session {})", saved.getId(), saved.getSession().getId());
        return toDto(saved);
    }

    @Transactional
    public void deletePhoto(UUID sessionId, UUID photoId) {
        ScoutingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("ScoutingSession", "id", sessionId));

        enforceAssignedScout(session);
        ensureSessionAllowsPhotoChanges(session);

        ScoutingPhoto photo = photoRepository.findByIdAndSessionId(photoId, sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("ScoutingPhoto", "id", photoId));

        if (photo.isDeleted()) {
            return;
        }

        photo.markDeleted();
        photo.setSyncStatus(SyncStatus.PENDING_UPLOAD);
        photoRepository.save(photo);
    }

    private void enforceAssignedScout(ScoutingSession session) {
        if (farmAccessService.getCurrentUserRole() != Role.SCOUT) {
            throw new ForbiddenException("Only the assigned scout can manage scouting photos.");
        }

        if (session.getScout() == null || !session.getScout().getId().equals(currentUserService.getCurrentUserId())) {
            throw new ForbiddenException("You are not assigned to this scouting session.");
        }
    }

    private void enforceSameSession(ScoutingSession session, ScoutingPhoto photo) {
        enforceSameSession(session.getId(), photo);
    }

    private void enforceSameSession(java.util.UUID sessionId, ScoutingPhoto photo) {
        if (!photo.getSession().getId().equals(sessionId)) {
            throw new BadRequestException("Photo belongs to a different session");
        }
    }

    private CellPhotoContext resolveContextFromRequest(ScoutingSession session, PhotoMetadataRequest request) {
        if (request.sessionTargetId() == null
                || request.bayIndex() == null
                || request.benchIndex() == null
                || request.spotIndex() == null) {
            throw new BadRequestException("Cell photos require sessionTargetId, bayIndex, benchIndex, and spotIndex.");
        }

        ScoutingSessionTarget target = sessionTargetRepository.findByIdAndSessionId(request.sessionTargetId(), session.getId())
                .orElseThrow(() -> new ResourceNotFoundException("ScoutingSessionTarget", "id", request.sessionTargetId()));

        return new CellPhotoContext(
                target,
                request.bayIndex(),
                request.bayTag(),
                request.benchIndex(),
                request.benchTag(),
                request.spotIndex()
        );
    }

    private void assertPhotoLimitNotExceeded(UUID sessionId, String localPhotoId, CellPhotoContext context) {
        Optional<ScoutingPhoto> existing = photoRepository.findByLocalPhotoIdAndDeletedFalse(localPhotoId);
        if (existing.isPresent()) {
            return;
        }

        long currentCount = photoRepository.countBySessionIdAndSessionTarget_IdAndBayIndexAndBenchIndexAndSpotIndexAndDeletedFalse(
                sessionId,
                context.sessionTarget().getId(),
                context.bayIndex(),
                context.benchIndex(),
                context.spotIndex()
        );
        if (currentCount >= MAX_ACTIVE_PHOTOS_PER_CELL) {
            throw new BadRequestException("A scouting cell can have at most 5 active photos.");
        }
    }

    private void ensureSessionAllowsPhotoChanges(ScoutingSession session) {
        if (session.getStatus() == SessionStatus.COMPLETED
                || session.getStatus() == SessionStatus.CANCELLED
                || session.getStatus() == SessionStatus.SUBMITTED) {
            throw new ForbiddenException("Photos can only be edited while the scouting session is still open.");
        }
    }

    private PhotoSourceType resolvePhotoSourceType(PhotoSourceType requestedSourceType, ScoutingSession session) {
        if (requestedSourceType != null) {
            return requestedSourceType;
        }
        if (session.getDefaultPhotoSourceType() != null) {
            return session.getDefaultPhotoSourceType();
        }
        return PhotoSourceType.SCOUT_HANDHELD;
    }

    private ScoutingPhotoDto toDto(ScoutingPhoto photo) {
        return new ScoutingPhotoDto(
                photo.getId(),
                photo.getSession().getId(),
                photo.getObservation() != null ? photo.getObservation().getId() : null,
                photo.getSessionTarget() != null ? photo.getSessionTarget().getId() : null,
                photo.getFarmId(),
                photo.getBayIndex(),
                photo.getBayLabel(),
                photo.getBenchIndex(),
                photo.getBenchLabel(),
                photo.getSpotIndex(),
                photo.getLocalPhotoId(),
                photo.getPurpose(),
                photo.getObjectKey(),
                photo.getSourceType(),
                photo.getCapturedAt(),
                photo.getUpdatedAt(),
                photo.getSyncStatus()
        );
    }

    private record CellPhotoContext(
            ScoutingSessionTarget sessionTarget,
            Integer bayIndex,
            String bayTag,
            Integer benchIndex,
            String benchTag,
            Integer spotIndex
    ) {
        private static CellPhotoContext fromObservation(ScoutingObservation observation) {
            return new CellPhotoContext(
                    observation.getSessionTarget(),
                    observation.getBayIndex(),
                    observation.getBayLabel(),
                    observation.getBenchIndex(),
                    observation.getBenchLabel(),
                    observation.getSpotIndex()
            );
        }
    }
}
