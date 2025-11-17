package mofo.com.pestscout.farm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mofo.com.pestscout.farm.dto.CreateGreenhouseRequest;
import mofo.com.pestscout.farm.dto.GreenhouseDto;
import mofo.com.pestscout.farm.dto.UpdateGreenhouseRequest;
import mofo.com.pestscout.farm.service.GreenhouseService;
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
public class GreenhouseController {

    private static final Logger LOGGER = LoggerFactory.getLogger(GreenhouseController.class);

    private final GreenhouseService greenhouseService;

    @PostMapping("/farms/{farmId}/greenhouses")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<GreenhouseDto> createGreenhouse(@PathVariable UUID farmId,
                                                          @Valid @RequestBody CreateGreenhouseRequest request) {
        LOGGER.info("POST /api/farms/{}/greenhouses — creating greenhouse", farmId);
        GreenhouseDto greenhouse = greenhouseService.createGreenhouse(farmId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(greenhouse);
    }

    @PutMapping("/greenhouses/{greenhouseId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','FARM_ADMIN','MANAGER')")
    public ResponseEntity<GreenhouseDto> updateGreenhouse(@PathVariable UUID greenhouseId,
                                                          @Valid @RequestBody UpdateGreenhouseRequest request) {
        LOGGER.info("PUT /api/greenhouses/{} — updating greenhouse", greenhouseId);
        return ResponseEntity.ok(greenhouseService.updateGreenhouse(greenhouseId, request));
    }

    @DeleteMapping("/greenhouses/{greenhouseId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteGreenhouse(@PathVariable UUID greenhouseId) {
        LOGGER.info("DELETE /api/greenhouses/{} — deleting greenhouse", greenhouseId);
        greenhouseService.deleteGreenhouse(greenhouseId);
    }
}
