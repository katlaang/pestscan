package mofo.com.pestscout.farm.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import mofo.com.pestscout.auth.security.JwtTokenProvider;
import mofo.com.pestscout.farm.dto.CreateFieldBlockRequest;
import mofo.com.pestscout.farm.dto.FieldBlockDto;
import mofo.com.pestscout.farm.dto.UpdateFieldBlockRequest;
import mofo.com.pestscout.farm.model.CropType;
import mofo.com.pestscout.farm.model.FieldBlockType;
import mofo.com.pestscout.farm.service.FieldBlockService;
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
        CreateFieldBlockRequest request = new CreateFieldBlockRequest("Block A", FieldBlockType.OPEN_FIELD, CropType.TOMATOES, 10.5, "North field");
        FieldBlockDto dto = new FieldBlockDto(UUID.randomUUID(), "Block A", FieldBlockType.OPEN_FIELD, CropType.TOMATOES, 10.5, "North field");

        when(fieldBlockService.createFieldBlock(any(UUID.class), any(CreateFieldBlockRequest.class))).thenReturn(dto);

        mockMvc.perform(post("/api/farms/" + farmId + "/field-blocks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Block A"));
    }

    @Test
    void updatesFieldBlock() throws Exception {
        UUID blockId = UUID.randomUUID();
        UpdateFieldBlockRequest request = new UpdateFieldBlockRequest("Block B", FieldBlockType.ORCHARD, CropType.STRAWBERRIES, 11.0, "Updated");
        FieldBlockDto dto = new FieldBlockDto(blockId, "Block B", FieldBlockType.ORCHARD, CropType.STRAWBERRIES, 11.0, "Updated");

        when(fieldBlockService.updateFieldBlock(any(UUID.class), any(UpdateFieldBlockRequest.class))).thenReturn(dto);

        mockMvc.perform(put("/api/field-blocks/" + blockId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(blockId.toString()));
    }
}
