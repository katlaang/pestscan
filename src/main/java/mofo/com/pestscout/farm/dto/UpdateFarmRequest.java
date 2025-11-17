package mofo.com.pestscout.farm.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record UpdateFarmRequest(

        @NotBlank
        @Size(max = 255)
        String name,

        @Size(max = 500)
        String description,

        @Size(max = 255)
        String externalId,

        @Size(max = 255)
        String address,

        BigDecimal latitude,
        BigDecimal longitude,

        @Size(max = 100)
        String city,

        @Size(max = 100)
        String province,

        @Size(max = 20)
        String postalCode,

        @Size(max = 100)
        String country,

        @Size(max = 255)
        String contactName,

        @Email
        @Size(max = 255)
        String contactEmail,

        @Size(max = 50)
        String contactPhone,

        Integer defaultBayCount,
        Integer defaultBenchesPerBay,
        Integer defaultSpotChecksPerBench,

        @Size(max = 100)
        String timezone
) {
}
