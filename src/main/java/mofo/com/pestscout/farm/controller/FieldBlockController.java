package mofo.com.pestscout.farm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mofo.com.pestscout.farm.dto.CreateFieldBlockRequest;
import mofo.com.pestscout.farm.dto.FieldBlockDto;
import mofo.com.pestscout.farm.dto.UpdateFieldBlockRequest;
import mofo.com.pestscout.farm.service.FieldBlockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class FieldBlockController {

    private static final Logger LOGGER = LoggerFactory.getLogger(FieldBlockController.class);

    private final FieldBlockService fieldBlockService;

    @PostMapping("/farms/{farmId}/field-blocks")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<FieldBlockDto> createFieldBlock(@PathVariable UUID farmId,
                                                          @Valid @RequestBody CreateFieldBlockRequest request) {
        LOGGER.info("POST /api/farms/{}/field-blocks — creating field block", farmId);
        FieldBlockDto block = fieldBlockService.createFieldBlock(farmId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(block);
    }

    @PutMapping("/field-blocks/{fieldBlockId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','FARM_ADMIN','MANAGER')")
    public ResponseEntity<FieldBlockDto> updateFieldBlock(@PathVariable UUID fieldBlockId,
                                                          @Valid @RequestBody UpdateFieldBlockRequest request) {
        LOGGER.info("PUT /api/field-blocks/{} — updating field block", fieldBlockId);
        return ResponseEntity.ok(fieldBlockService.updateFieldBlock(fieldBlockId, request));
    }

    @DeleteMapping("/field-blocks/{fieldBlockId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFieldBlock(@PathVariable UUID fieldBlockId) {
        LOGGER.info("DELETE /api/field-blocks/{} — deleting field block", fieldBlockId);
        fieldBlockService.deleteFieldBlock(fieldBlockId);
    }
}
