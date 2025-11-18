package mofo.com.pestscout.farm.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import mofo.com.pestscout.farm.dto.CreateFarmRequest;
import mofo.com.pestscout.farm.dto.FarmResponse;
import mofo.com.pestscout.farm.dto.UpdateFarmRequest;
import mofo.com.pestscout.farm.model.SubscriptionStatus;
import mofo.com.pestscout.farm.model.SubscriptionTier;
import mofo.com.pestscout.farm.service.FarmService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
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

    @MockBean
    private FarmService farmService;

    @Test
    void createsFarmAndReturnsResponse() throws Exception {
        UUID farmId = UUID.randomUUID();
        CreateFarmRequest request = new CreateFarmRequest(
                "Farm",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                UUID.randomUUID(),
                null,
                null,
                null,
                null,
                SubscriptionStatus.ACTIVE,
                SubscriptionTier.BASIC,
                null,
                BigDecimal.ONE,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        FarmResponse response = new FarmResponse(
                farmId,
                "TAG-1",
                "Farm",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                SubscriptionStatus.ACTIVE,
                SubscriptionTier.BASIC,
                null,
                BigDecimal.ONE,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of(),
                null,
                null,
                null,
                null,
                null
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
                "TAG",
                "Farm",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                SubscriptionStatus.ACTIVE,
                SubscriptionTier.BASIC,
                null,
                BigDecimal.ONE,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of(),
                null,
                null,
                null,
                null,
                null
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
                "Updated",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        FarmResponse response = new FarmResponse(
                farmId,
                "TAG-1",
                "Updated",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                SubscriptionStatus.ACTIVE,
                SubscriptionTier.BASIC,
                null,
                BigDecimal.ONE,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of(),
                null,
                null,
                null,
                null,
                null
        );

        when(farmService.updateFarm(eq(farmId), any(UpdateFarmRequest.class))).thenReturn(response);

        mockMvc.perform(put("/api/farms/{farmId}", farmId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated"));
    }
}
