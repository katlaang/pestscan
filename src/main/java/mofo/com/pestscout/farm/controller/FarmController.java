package mofo.com.pestscout.farm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mofo.com.pestscout.farm.dto.CreateFarmRequest;
import mofo.com.pestscout.farm.dto.FarmResponse;
import mofo.com.pestscout.farm.dto.UpdateFarmRequest;
import mofo.com.pestscout.farm.service.FarmService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/farms")
@RequiredArgsConstructor
public class FarmController {

    private static final Logger LOGGER = LoggerFactory.getLogger(FarmController.class);

    private final FarmService farmService;

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<FarmResponse> createFarm(@Valid @RequestBody CreateFarmRequest request) {
        LOGGER.info("POST /api/farms — creating farm");
        FarmResponse farm = farmService.createFarm(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(farm);
    }

    @PutMapping("/{farmId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','FARM_ADMIN','MANAGER')")
    public ResponseEntity<FarmResponse> updateFarm(@PathVariable UUID farmId,
                                                   @Valid @RequestBody UpdateFarmRequest request) {
        LOGGER.info("PUT /api/farms/{} — updating farm", farmId);
        return ResponseEntity.ok(farmService.updateFarm(farmId, request));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<FarmResponse>> listFarms() {
        LOGGER.info("GET /api/farms — listing farms for current user");
        return ResponseEntity.ok(farmService.listFarms());
    }

    @GetMapping("/{farmId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<FarmResponse> getFarm(@PathVariable UUID farmId) {
        LOGGER.info("GET /api/farms/{} — loading farm", farmId);
        return ResponseEntity.ok(farmService.getFarm(farmId));
    }

}
