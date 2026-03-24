package mofo.com.pestscout.scouting.service;

import mofo.com.pestscout.auth.model.Role;
import mofo.com.pestscout.auth.model.User;
import mofo.com.pestscout.common.exception.BadRequestException;
import mofo.com.pestscout.common.exception.ForbiddenException;
import mofo.com.pestscout.common.model.SyncStatus;
import mofo.com.pestscout.farm.model.Farm;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ScoutingPhotoService Unit Tests")
class ScoutingPhotoServiceTest {

    @Mock
    private ScoutingSessionRepository sessionRepository;

    @Mock
    private ScoutingObservationRepository observationRepository;

    @Mock
    private ScoutingPhotoRepository photoRepository;

    @Mock
    private ScoutingSessionTargetRepository sessionTargetRepository;

    @Mock
    private FarmAccessService farmAccessService;

    @Mock
    private CurrentUserService currentUserService;

    @InjectMocks
    private ScoutingPhotoService scoutingPhotoService;

    private User scout;
    private User manager;
    private ScoutingSession session;
    private ScoutingSessionTarget target;

    @BeforeEach
    void setUp() {
        scout = User.builder()
                .id(UUID.randomUUID())
                .email("scout@example.com")
                .role(Role.SCOUT)
                .isEnabled(true)
                .build();

        manager = User.builder()
                .id(UUID.randomUUID())
                .email("manager@example.com")
                .role(Role.MANAGER)
                .isEnabled(true)
                .build();

        Farm farm = Farm.builder()
                .id(UUID.randomUUID())
                .owner(manager)
                .scout(scout)
                .build();

        session = ScoutingSession.builder()
                .id(UUID.randomUUID())
                .farm(farm)
                .scout(scout)
                .status(SessionStatus.IN_PROGRESS)
                .defaultPhotoSourceType(PhotoSourceType.SCOUT_HANDHELD)
                .build();

        target = ScoutingSessionTarget.builder()
                .id(UUID.randomUUID())
                .session(session)
                .includeAllBays(true)
                .includeAllBenches(true)
                .build();
    }

    @Test
    void registerMetadata_WithAssignedScout_CreatesPhoto() {
        PhotoMetadataRequest request = new PhotoMetadataRequest(
                session.getId(),
                null,
                target.getId(),
                1,
                "Bay-1",
                1,
                "Bed-1",
                1,
                "photo-1",
                "Leaf close-up",
                null,
                LocalDateTime.now()
        );

        when(farmAccessService.getCurrentUserRole()).thenReturn(Role.SCOUT);
        when(currentUserService.getCurrentUserId()).thenReturn(scout.getId());
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(sessionTargetRepository.findByIdAndSessionId(target.getId(), session.getId())).thenReturn(Optional.of(target));
        when(photoRepository.findByLocalPhotoIdAndDeletedFalse("photo-1")).thenReturn(Optional.empty());
        when(photoRepository.countBySessionIdAndSessionTarget_IdAndBayIndexAndBenchIndexAndSpotIndexAndDeletedFalse(
                session.getId(), target.getId(), 1, 1, 1
        )).thenReturn(0L);
        when(photoRepository.save(any(ScoutingPhoto.class))).thenAnswer(invocation -> {
            ScoutingPhoto photo = invocation.getArgument(0);
            photo.setId(UUID.randomUUID());
            return photo;
        });

        ScoutingPhotoDto result = scoutingPhotoService.registerMetadata(request);

        assertThat(result).isNotNull();
        assertThat(result.localPhotoId()).isEqualTo("photo-1");
        assertThat(result.sessionTargetId()).isEqualTo(target.getId());
        assertThat(result.bayIndex()).isEqualTo(1);
        assertThat(result.benchIndex()).isEqualTo(1);
        assertThat(result.spotIndex()).isEqualTo(1);
        assertThat(result.sourceType()).isEqualTo(PhotoSourceType.SCOUT_HANDHELD);
        assertThat(result.syncStatus()).isEqualTo(SyncStatus.PENDING_UPLOAD);
    }

    @Test
    void registerMetadata_WithSessionRemarkPhoto_CreatesPhotoWithoutCellContext() {
        PhotoMetadataRequest request = new PhotoMetadataRequest(
                session.getId(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "photo-remark-1",
                "Hotspot in remarks",
                null,
                LocalDateTime.now()
        );

        when(farmAccessService.getCurrentUserRole()).thenReturn(Role.SCOUT);
        when(currentUserService.getCurrentUserId()).thenReturn(scout.getId());
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(photoRepository.findByLocalPhotoIdAndDeletedFalse("photo-remark-1")).thenReturn(Optional.empty());
        when(photoRepository.save(any(ScoutingPhoto.class))).thenAnswer(invocation -> {
            ScoutingPhoto photo = invocation.getArgument(0);
            photo.setId(UUID.randomUUID());
            return photo;
        });

        ScoutingPhotoDto result = scoutingPhotoService.registerMetadata(request);

        assertThat(result).isNotNull();
        assertThat(result.localPhotoId()).isEqualTo("photo-remark-1");
        assertThat(result.sessionTargetId()).isNull();
        assertThat(result.bayIndex()).isNull();
        assertThat(result.benchIndex()).isNull();
        assertThat(result.spotIndex()).isNull();
        assertThat(result.purpose()).isEqualTo("Hotspot in remarks");
    }

    @Test
    void registerMetadata_WithManagerRole_ThrowsForbiddenException() {
        PhotoMetadataRequest request = new PhotoMetadataRequest(
                session.getId(),
                null,
                target.getId(),
                1,
                "Bay-1",
                1,
                "Bed-1",
                1,
                "photo-1",
                "Leaf close-up",
                null,
                LocalDateTime.now()
        );

        when(farmAccessService.getCurrentUserRole()).thenReturn(Role.MANAGER);
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> scoutingPhotoService.registerMetadata(request))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("assigned scout");
    }

    @Test
    void confirmUpload_WithAssignedScout_UpdatesObjectKey() {
        ScoutingPhoto photo = ScoutingPhoto.builder()
                .id(UUID.randomUUID())
                .session(session)
                .sessionTarget(target)
                .farmId(session.getFarm().getId())
                .bayIndex(1)
                .benchIndex(1)
                .spotIndex(1)
                .localPhotoId("photo-1")
                .sourceType(PhotoSourceType.SCOUT_HANDHELD)
                .build();

        PhotoUploadConfirmationRequest request = new PhotoUploadConfirmationRequest(
                session.getId(),
                "photo-1",
                "photos/session/photo-1.jpg"
        );

        when(farmAccessService.getCurrentUserRole()).thenReturn(Role.SCOUT);
        when(currentUserService.getCurrentUserId()).thenReturn(scout.getId());
        when(photoRepository.findByLocalPhotoIdAndDeletedFalse("photo-1")).thenReturn(Optional.of(photo));
        when(photoRepository.save(any(ScoutingPhoto.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ScoutingPhotoDto result = scoutingPhotoService.confirmUpload(request);

        assertThat(result.objectKey()).isEqualTo("photos/session/photo-1.jpg");
        assertThat(result.syncStatus()).isEqualTo(SyncStatus.SYNCED);
    }

    @Test
    void registerMetadata_WithFiveExistingCellPhotos_ThrowsBadRequestException() {
        PhotoMetadataRequest request = new PhotoMetadataRequest(
                session.getId(),
                null,
                target.getId(),
                1,
                "Bay-1",
                1,
                "Bed-1",
                1,
                "photo-6",
                "Leaf close-up",
                null,
                LocalDateTime.now()
        );

        when(farmAccessService.getCurrentUserRole()).thenReturn(Role.SCOUT);
        when(currentUserService.getCurrentUserId()).thenReturn(scout.getId());
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(sessionTargetRepository.findByIdAndSessionId(target.getId(), session.getId())).thenReturn(Optional.of(target));
        when(photoRepository.findByLocalPhotoIdAndDeletedFalse("photo-6")).thenReturn(Optional.empty());
        when(photoRepository.countBySessionIdAndSessionTarget_IdAndBayIndexAndBenchIndexAndSpotIndexAndDeletedFalse(
                eq(session.getId()), eq(target.getId()), eq(1), eq(1), eq(1)
        )).thenReturn(5L);

        assertThatThrownBy(() -> scoutingPhotoService.registerMetadata(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("at most 5 active photos");
    }
}
