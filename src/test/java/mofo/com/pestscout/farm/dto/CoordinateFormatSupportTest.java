package mofo.com.pestscout.farm.dto;

import mofo.com.pestscout.common.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CoordinateFormatSupportTest {

    @Test
    void parsesDirectionalCoordinates() {
        assertThat(CoordinateFormatSupport.parseLatitude("1.292066° S"))
                .isEqualByComparingTo("-1.292066");
        assertThat(CoordinateFormatSupport.parseLongitude("104.0204° W"))
                .isEqualByComparingTo("-104.0204");
    }

    @Test
    void formatsDirectionalCoordinates() {
        assertThat(CoordinateFormatSupport.formatLatitude(new BigDecimal("-1.292066")))
                .isEqualTo("1.292066° S");
        assertThat(CoordinateFormatSupport.formatLongitude(new BigDecimal("104.0204")))
                .isEqualTo("104.0204° E");
    }

    @Test
    void rejectsConflictingCoordinateDirection() {
        assertThatThrownBy(() -> CoordinateFormatSupport.parseLongitude("-104.0204° E"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("sign conflicts");
    }
}
