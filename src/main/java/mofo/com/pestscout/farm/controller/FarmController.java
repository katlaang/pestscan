package mofo.com.pestscout.farm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mofo.com.pestscout.farm.dto.FarmRequest;
import mofo.com.pestscout.farm.dto.FarmResponse;
import mofo.com.pestscout.farm.service.FarmService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/farms")
@RequiredArgsConstructor
public class FarmController {

    private final FarmService farmService;

    @PostMapping
    public ResponseEntity<FarmResponse> createFarm(@Valid @RequestBody FarmRequest request) {
        FarmResponse farm = farmService.createFarm(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(farm);
    }

    @PutMapping("/{farmId}")
    public ResponseEntity<FarmResponse> updateFarm(@PathVariable UUID farmId,
                                                   @Valid @RequestBody FarmRequest request) {
        return ResponseEntity.ok(farmService.updateFarm(farmId, request));
    }

    @GetMapping
    public ResponseEntity<List<FarmResponse>> listFarms() {
        return ResponseEntity.ok(farmService.listFarms());
    }

    @GetMapping("/{farmId}")
    public ResponseEntity<FarmResponse> getFarm(@PathVariable UUID farmId) {
        return ResponseEntity.ok(farmService.getFarm(farmId));
    }

}
