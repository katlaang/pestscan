package mofo.com.pestscout.common.model;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Base entity class with common fields for all entities
 * Provides:
 * - UUID primary key
 * - Created and updated timestamps (automatically managed)
 * - Audit trail support
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
        if (syncStatus == null) {
            syncStatus = SyncStatus.SYNCED;
        }
        // hook for subclasses
        applyPrePersistDefaults();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    @Column(name = "deleted", nullable = false)
    private boolean deleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "sync_status", nullable = false, length = 32)
    private SyncStatus syncStatus = SyncStatus.SYNCED;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BaseEntity that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Version
    @Column(name = "version")
    private Long version;

    /**
     * Hook method for entities that need extra defaults on persist.
     * Default is no-op.
     */
    protected void applyPrePersistDefaults() {
        // no default implementation
    }

    /**
     * Soft-delete the entity while retaining it for sync/audit scenarios.
     */
    public void markDeleted() {
        this.deleted = true;
        this.deletedAt = LocalDateTime.now();
    }

    /**
     * Restore a soft-deleted entity (used when recreating the same logical row).
     */
    public void restore() {
        this.deleted = false;
        this.deletedAt = null;
    }
}
