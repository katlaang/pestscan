package mofo.com.pestscout.farm.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import mofo.com.pestscout.auth.security.JwtTokenProvider;
import mofo.com.pestscout.farm.dto.CreateFieldBlockRequest;
import mofo.com.pestscout.farm.dto.FieldBlockDto;
import mofo.com.pestscout.farm.dto.UpdateFieldBlockRequest;
import mofo.com.pestscout.farm.service.FieldBlockService;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = FieldBlockController.class)
@AutoConfigureMockMvc(addFilters = false)
class FieldBlockControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private FieldBlockService fieldBlockService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void createsFieldBlock() throws Exception {
        UUID farmId = UUID.randomUUID();

        CreateFieldBlockRequest request = new CreateFieldBlockRequest(
                "Block A",
                10,                    // bayCount
                3,                     // spotChecksPerBay
                List.of("Bay-1", "Bay-2"),
                true                   // active
        );

        // We do not depend on FieldBlockDto fields here, just the status code
        when(fieldBlockService.createFieldBlock(any(UUID.class), any(CreateFieldBlockRequest.class)))
                .thenReturn(mock(FieldBlockDto.class));

        mockMvc.perform(post("/api/farms/{farmId}/field-blocks", farmId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    void updatesFieldBlock() throws Exception {
        UUID blockId = UUID.randomUUID();

        UpdateFieldBlockRequest request = new UpdateFieldBlockRequest(
                "Block B",
                12,                    // bayCount
                4,                     // spotChecksPerBay
                List.of("Bay-1"),
                true
        );

        when(fieldBlockService.updateFieldBlock(any(UUID.class), any(UpdateFieldBlockRequest.class)))
                .thenReturn(mock(FieldBlockDto.class));

        mockMvc.perform(put("/api/field-blocks/{blockId}", blockId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }
}
