package mofo.com.pestscout.scouting.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mofo.com.pestscout.scouting.dto.CreateCustomSpeciesRequest;
import mofo.com.pestscout.scouting.dto.CustomSpeciesDto;
import mofo.com.pestscout.scouting.model.ObservationCategory;
import mofo.com.pestscout.scouting.service.CustomSpeciesService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/farms/{farmId}/custom-species")
@RequiredArgsConstructor
public class CustomSpeciesController {

    private static final Logger LOGGER = LoggerFactory.getLogger(CustomSpeciesController.class);

    private final CustomSpeciesService customSpeciesService;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','FARM_ADMIN','MANAGER','SCOUT')")
    public ResponseEntity<List<CustomSpeciesDto>> listCustomSpecies(@PathVariable UUID farmId,
                                                                    @RequestParam(required = false) ObservationCategory category) {
        LOGGER.info("GET /api/farms/{}/custom-species - listing custom species", farmId);
        return ResponseEntity.ok(customSpeciesService.listFarmCustomSpecies(farmId, category));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','FARM_ADMIN','MANAGER','SCOUT')")
    public ResponseEntity<List<CustomSpeciesDto>> createCustomSpecies(@PathVariable UUID farmId,
                                                                      @Valid @RequestBody CreateCustomSpeciesRequest request) {
        LOGGER.info("POST /api/farms/{}/custom-species - creating custom species", farmId);
        return ResponseEntity.status(HttpStatus.CREATED).body(customSpeciesService.createCustomSpecies(farmId, request));
    }
}
