package mofo.com.pestscout.region.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("NorthAmericaRegionService Unit Tests")
class NorthAmericaRegionServiceTest {

    private final NorthAmericaRegionService service = new NorthAmericaRegionService();

    @Test
    void normalizeCountry_ResolvesCanonicalName() {
        assertThat(service.normalizeCountry(" united states ")).isEqualTo("United States");
        assertThat(service.normalizeCountry("CANADA")).isEqualTo("Canada");
    }

    @Test
    void normalizeState_ResolvesCanonicalSubdivision() {
        assertThat(service.normalizeState("Canada", "ontario")).isEqualTo("Ontario");
        assertThat(service.normalizeState("Mexico", "nuevo leon")).isEqualTo("Nuevo Leon");
    }

    @Test
    void matchesRegion_AllowsCountryWideAlerts() {
        assertThat(service.matchesRegion("Canada", "Ontario", "Canada", null)).isTrue();
        assertThat(service.matchesRegion("Canada", "Ontario", "Canada", "Ontario")).isTrue();
        assertThat(service.matchesRegion("Canada", "Ontario", "Canada", "Quebec")).isFalse();
    }

    @Test
    void normalizeCountry_RejectsUnsupportedCountry() {
        assertThatThrownBy(() -> service.normalizeCountry("Kenya"))
                .hasMessageContaining("Country must be one of");
    }
}
