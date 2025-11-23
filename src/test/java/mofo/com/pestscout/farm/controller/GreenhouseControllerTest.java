package mofo.com.pestscout.farm.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import mofo.com.pestscout.auth.security.JwtTokenProvider;
import mofo.com.pestscout.farm.dto.CreateGreenhouseRequest;
import mofo.com.pestscout.farm.dto.GreenhouseDto;
import mofo.com.pestscout.farm.dto.UpdateGreenhouseRequest;
import mofo.com.pestscout.farm.service.GreenhouseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = GreenhouseController.class)
@AutoConfigureMockMvc(addFilters = false)
class GreenhouseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private GreenhouseService greenhouseService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void createsGreenhouse() throws Exception {
        UUID farmId = UUID.randomUUID();
        CreateGreenhouseRequest request = new CreateGreenhouseRequest("G1", 4, 10, "Notes");
        GreenhouseDto dto = new GreenhouseDto(UUID.randomUUID(), "G1", 4, 10, "Notes");

        when(greenhouseService.createGreenhouse(any(UUID.class), any(CreateGreenhouseRequest.class))).thenReturn(dto);

        mockMvc.perform(post("/api/farms/" + farmId + "/greenhouses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("G1"));
    }

    @Test
    void updatesGreenhouse() throws Exception {
        UUID greenhouseId = UUID.randomUUID();
        UpdateGreenhouseRequest request = new UpdateGreenhouseRequest("G2", 5, 12, "Updated");
        GreenhouseDto dto = new GreenhouseDto(greenhouseId, "G2", 5, 12, "Updated");

        when(greenhouseService.updateGreenhouse(any(UUID.class), any(UpdateGreenhouseRequest.class))).thenReturn(dto);

        mockMvc.perform(put("/api/greenhouses/" + greenhouseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(greenhouseId.toString()));
    }
}
