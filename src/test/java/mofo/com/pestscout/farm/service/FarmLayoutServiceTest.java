package mofo.com.pestscout.farm.service;

import mofo.com.pestscout.common.exception.BadRequestException;
import mofo.com.pestscout.farm.dto.FarmLayoutPreviewRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FarmLayoutServiceTest {

    private final FarmLayoutService farmLayoutService = new FarmLayoutService();

    @Test
    void previewLayout_WithCoordinates_ReturnsGeoReferencedLayout() {
        FarmLayoutPreviewRequest request = new FarmLayoutPreviewRequest(
                new BigDecimal("43.123456"),
                new BigDecimal("-80.123456"),
                3,
                List.of("House A", "House B", "House C")
        );

        var response = farmLayoutService.previewLayout(request);

        assertThat(response.greenhouseCount()).isEqualTo(3);
        assertThat(response.geoReferenced()).isTrue();
        assertThat(response.columns()).isEqualTo(2);
        assertThat(response.greenhouses()).hasSize(3);
        assertThat(response.greenhouses().getFirst().centerLatitude()).isNotNull();
        assertThat(response.greenhouses().getFirst().polygon()).hasSize(5);
    }

    @Test
    void previewLayout_WithoutCoordinates_ReturnsLocalLayout() {
        FarmLayoutPreviewRequest request = new FarmLayoutPreviewRequest(
                null,
                null,
                2,
                null
        );

        var response = farmLayoutService.previewLayout(request);

        assertThat(response.geoReferenced()).isFalse();
        assertThat(response.layoutMode()).isEqualTo("LOCAL_GREENHOUSE_LAYOUT");
        assertThat(response.greenhouses()).extracting(item -> item.name())
                .containsExactly("Greenhouse 1", "Greenhouse 2");
    }

    @Test
    void previewLayout_WithInvalidCount_ThrowsBadRequest() {
        FarmLayoutPreviewRequest request = new FarmLayoutPreviewRequest(
                null,
                null,
                0,
                null
        );

        assertThatThrownBy(() -> farmLayoutService.previewLayout(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("greenhouseCount");
    }
}
