package mofo.com.pestscout.region.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import mofo.com.pestscout.region.dto.SupportedRegionDto;
import mofo.com.pestscout.region.service.NorthAmericaRegionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/regions")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class RegionReferenceController {

    private final NorthAmericaRegionService northAmericaRegionService;

    @GetMapping("/north-america")
    @Operation(summary = "Supported North America geography reference")
    public ResponseEntity<List<SupportedRegionDto>> getNorthAmericaRegions() {
        return ResponseEntity.ok(northAmericaRegionService.getSupportedRegions());
    }
}
