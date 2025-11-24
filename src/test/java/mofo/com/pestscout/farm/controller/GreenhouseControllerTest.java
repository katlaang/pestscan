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

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
        CreateGreenhouseRequest request = new CreateGreenhouseRequest("G1", "tomato bay", 10, 2, 5, List.of("Bay-1"), List.of("Bench-1"));
        GreenhouseDto dto = new GreenhouseDto(UUID.randomUUID(), 1L, UUID.randomUUID(), "Bay10", "tomato bay", 2, 1, 2, List.of("bays"), List.of("tomato1", "tomato2", "tomato 3", "tomato4"), true);

        when(greenhouseService.createGreenhouse(any(UUID.class), any(CreateGreenhouseRequest.class))).thenReturn(dto);

        mockMvc.perform(post("/api/farms/" + farmId + "/greenhouses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Bay10"));
    }

    @Test
    void updatesGreenhouse() throws Exception {
        UUID greenhouseId = UUID.randomUUID();

        UpdateGreenhouseRequest request = new UpdateGreenhouseRequest(
                "Bay10",
                2,
                1,
                2,
                true,
                "tomato bay",
                List.of("bays"),
                List.of("tomato1", "tomato2", "tomato 3", "tomato4")
        );

        UUID farmId = UUID.randomUUID();

        GreenhouseDto dto = new GreenhouseDto(
                greenhouseId,                // match the path id
                1L,                          // version
                farmId,
                "Bay10",
                "tomato bay",
                2,
                1,
                2,
                List.of("bays"),
                List.of("tomato1", "tomato2", "tomato 3", "tomato4"),
                true
        );

        when(greenhouseService.updateGreenhouse(
                org.mockito.ArgumentMatchers.eq(greenhouseId),
                any(UpdateGreenhouseRequest.class)
        )).thenReturn(dto);

        mockMvc.perform(put("/api/greenhouses/" + greenhouseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(greenhouseId.toString()));
    }

}
