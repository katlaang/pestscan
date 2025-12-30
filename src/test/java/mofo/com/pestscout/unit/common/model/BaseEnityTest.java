package mofo.com.pestscout.unit.common.model;

import mofo.com.pestscout.common.model.BaseEntity;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class BaseEntityTest {

    private static class TestEntity extends BaseEntity {
    }

    @Test
    void markDeleted_setsDeletedAndDeletedAt() {
        TestEntity e = new TestEntity();
        e.markDeleted();

        assertThat(e.isDeleted()).isTrue();
        assertThat(e.getDeletedAt()).isNotNull();
    }

    @Test
    void restore_clearsDeletedFlags() {
        TestEntity e = new TestEntity();
        e.markDeleted();

        e.restore();

        assertThat(e.isDeleted()).isFalse();
        assertThat(e.getDeletedAt()).isNull();
    }

    @Test
    void equalsUsesIdOnly() {
        UUID id = UUID.randomUUID();
        TestEntity a = new TestEntity();
        a.setId(id);
        TestEntity b = new TestEntity();
        b.setId(id);

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }
}
