package mofo.com.pestscout.farm.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import mofo.com.pestscout.auth.security.JwtTokenProvider;
import mofo.com.pestscout.farm.dto.CreateFarmRequest;
import mofo.com.pestscout.farm.dto.FarmResponse;
import mofo.com.pestscout.farm.dto.UpdateFarmRequest;
import mofo.com.pestscout.farm.model.FarmStructureType;
import mofo.com.pestscout.farm.model.SubscriptionStatus;
import mofo.com.pestscout.farm.model.SubscriptionTier;
import mofo.com.pestscout.farm.service.FarmService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = FarmController.class)
@AutoConfigureMockMvc(addFilters = false)
class FarmControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private FarmService farmService;

    // Provide a JwtTokenProvider bean so JwtAuthenticationFilter can be constructed
    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void createsFarmAndReturnsResponse() throws Exception {
        UUID farmId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID scoutId = UUID.randomUUID();

        CreateFarmRequest request = new CreateFarmRequest(
                "Farm",                         // name
                "Demo farm",                    // description
                "EXT-1",                        // externalId
                "123 Main St",                  // address
                "Nairobi",                      // city
                "Nairobi County",               // province
                "00100",                        // postalCode
                "Kenya",                        // country
                ownerId,                        // ownerId
                scoutId,                        // scoutId
                "Jane Doe",                     // contactName
                "jane@example.com",             // contactEmail
                "+254700000000",                // contactPhone
                SubscriptionStatus.ACTIVE,      // subscriptionStatus
                SubscriptionTier.BASIC,         // subscriptionTier
                "billing@example.com",          // billingEmail
                BigDecimal.valueOf(1.5),        // licensedAreaHectares
                100,                            // licensedUnitQuota
                BigDecimal.valueOf(10),         // quotaDiscountPercentage
                FarmStructureType.GREENHOUSE,   // structureType
                4,                              // defaultBayCount
                10,                             // defaultBenchesPerBay
                5,                              // defaultSpotChecksPerBench
                List.of(),                      // greenhouses
                List.of(),                      // fieldBlocks
                "Africa/Nairobi",               // timezone
                LocalDate.of(2026, 1, 1),       // licenseExpiryDate
                true                            // autoRenewEnabled
        );

        FarmResponse response = new FarmResponse(
                farmId,
                "TAG-1",                         // farmTag
                "Farm",                          // name
                "Demo farm",                     // description
                "EXT-1",                         // externalId
                "123 Main St",                   // address
                "Nairobi",                       // city
                "Nairobi County",                // province
                "00100",                         // postalCode
                "Kenya",                         // country
                "Jane Doe",                      // contactName
                "jane@example.com",              // contactEmail
                "+254700000000",                 // contactPhone
                SubscriptionStatus.ACTIVE,
                SubscriptionTier.BASIC,
                "billing@example.com",           // billingEmail
                BigDecimal.valueOf(1.5),         // licensedAreaHectares
                100,                             // licensedUnitQuota
                BigDecimal.valueOf(10),          // quotaDiscountPercentage
                LocalDate.of(2026, 1, 1),        // licenseExpiryDate
                true,                            // autoRenewEnabled
                false,                           // accessLocked
                FarmStructureType.GREENHOUSE,    // structureType
                4,                               // defaultBayCount
                10,                              // defaultBenchesPerBay
                5,                               // defaultSpotChecksPerBench
                Instant.parse("2024-01-01T00:00:00Z"), // createdAt
                Instant.parse("2024-01-02T00:00:00Z"), // updatedAt
                "Africa/Nairobi",                // timezone
                UUID.randomUUID(),               // ownerId
                UUID.randomUUID()                // scoutId
        );

        when(farmService.createFarm(any(CreateFarmRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/farms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(farmId.toString()));
    }

    @Test
    void listsFarmsFromService() throws Exception {
        FarmResponse farm = new FarmResponse(
                UUID.randomUUID(),
                "TAG-2",
                "Farm",
                "Demo farm",
                "EXT-2",
                "456 Other Rd",
                "Eldoret",
                "Uasin Gishu",
                "30100",
                "Kenya",
                "John Doe",
                "john@example.com",
                "+254711111111",
                SubscriptionStatus.ACTIVE,
                SubscriptionTier.BASIC,
                "billing@example.com",
                BigDecimal.valueOf(2.0),
                80,
                BigDecimal.ZERO,
                LocalDate.of(2025, 12, 31),
                true,
                false,
                FarmStructureType.GREENHOUSE,
                3,
                8,
                4,
                Instant.parse("2024-02-01T00:00:00Z"),
                Instant.parse("2024-02-02T00:00:00Z"),
                "Africa/Nairobi",
                UUID.randomUUID(),
                UUID.randomUUID()
        );

        when(farmService.listFarms()).thenReturn(List.of(farm));

        mockMvc.perform(get("/api/farms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Farm"));

        Mockito.verify(farmService).listFarms();
    }

    @Test
    void updatesFarmAndReturnsPayload() throws Exception {
        UUID farmId = UUID.randomUUID();

        UpdateFarmRequest updateRequest = new UpdateFarmRequest(
                "Updated",                         // name
                "Updated description",             // description
                "EXT-UPDATED",                     // externalId
                "789 New St",                      // address
                BigDecimal.valueOf(1.234),         // latitude
                BigDecimal.valueOf(36.789),        // longitude
                "Thika",                           // city
                "Kiambu",                          // province
                "01000",                           // postalCode
                "Kenya",                           // country
                "Alice Doe",                       // contactName
                "alice@example.com",               // contactEmail
                "+254722222222",                   // contactPhone
                5,                                 // defaultBayCount
                12,                                // defaultBenchesPerBay
                6,                                 // defaultSpotChecksPerBench
                "Africa/Nairobi"                   // timezone
        );

        FarmResponse response = new FarmResponse(
                farmId,
                "TAG-1",
                "Updated",
                "Updated description",
                "EXT-UPDATED",
                "789 New St",
                "Thika",
                "Kiambu",
                "01000",
                "Kenya",
                "Alice Doe",
                "alice@example.com",
                "+254722222222",
                SubscriptionStatus.ACTIVE,
                SubscriptionTier.BASIC,
                "billing@example.com",
                BigDecimal.valueOf(1.8),
                120,
                BigDecimal.valueOf(5),
                LocalDate.of(2026, 6, 30),
                true,
                false,
                FarmStructureType.GREENHOUSE,
                5,
                12,
                6,
                Instant.parse("2024-03-01T00:00:00Z"),
                Instant.parse("2024-03-10T00:00:00Z"),
                "Africa/Nairobi",
                UUID.randomUUID(),
                UUID.randomUUID()
        );

        when(farmService.updateFarm(eq(farmId), any(UpdateFarmRequest.class))).thenReturn(response);

        mockMvc.perform(put("/api/farms/{farmId}", farmId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated"));
    }
}
