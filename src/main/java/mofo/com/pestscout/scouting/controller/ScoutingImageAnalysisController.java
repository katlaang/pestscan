package mofo.com.pestscout.scouting.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mofo.com.pestscout.scouting.dto.ImageAnalysisDtos.PhotoAnalysisAccuracyResponse;
import mofo.com.pestscout.scouting.dto.ImageAnalysisDtos.PhotoAnalysisResponse;
import mofo.com.pestscout.scouting.dto.ImageAnalysisDtos.PhotoAnalysisReviewRequest;
import mofo.com.pestscout.scouting.dto.ImageAnalysisDtos.RunPhotoAnalysisRequest;
import mofo.com.pestscout.scouting.service.ScoutingImageAnalysisService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/scouting/photos")
@RequiredArgsConstructor
@Slf4j
public class ScoutingImageAnalysisController {

    private final ScoutingImageAnalysisService imageAnalysisService;

    @PostMapping("/{photoId}/analysis")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','FARM_ADMIN','MANAGER','SCOUT')")
    public ResponseEntity<PhotoAnalysisResponse> analyzePhoto(
            @PathVariable UUID photoId,
            @Valid @RequestBody RunPhotoAnalysisRequest request
    ) {
        log.info("POST /api/scouting/photos/{}/analysis - farm {}", photoId, request.farmId());
        return ResponseEntity.ok(imageAnalysisService.analyzePhoto(request.farmId(), photoId));
    }

    @GetMapping("/{photoId}/analysis")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','FARM_ADMIN','MANAGER','SCOUT')")
    public ResponseEntity<PhotoAnalysisResponse> getPhotoAnalysis(
            @PathVariable UUID photoId,
            @RequestParam UUID farmId
    ) {
        log.info("GET /api/scouting/photos/{}/analysis - farm {}", photoId, farmId);
        return ResponseEntity.ok(imageAnalysisService.getPhotoAnalysis(farmId, photoId));
    }

    @PutMapping("/{photoId}/analysis/review")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','FARM_ADMIN','MANAGER')")
    public ResponseEntity<PhotoAnalysisResponse> reviewPhotoAnalysis(
            @PathVariable UUID photoId,
            @Valid @RequestBody PhotoAnalysisReviewRequest request
    ) {
        log.info("PUT /api/scouting/photos/{}/analysis/review - farm {}", photoId, request.farmId());
        return ResponseEntity.ok(imageAnalysisService.reviewPhotoAnalysis(photoId, request));
    }

    @GetMapping("/analysis/accuracy")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','FARM_ADMIN','MANAGER')")
    public ResponseEntity<PhotoAnalysisAccuracyResponse> getAccuracy(@RequestParam UUID farmId) {
        log.info("GET /api/scouting/photos/analysis/accuracy - farm {}", farmId);
        return ResponseEntity.ok(imageAnalysisService.getAccuracy(farmId));
    }
}
