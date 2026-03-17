package mofo.com.pestscout.scouting.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import mofo.com.pestscout.auth.security.JwtTokenProvider;
import mofo.com.pestscout.scouting.dto.ImageAnalysisDtos.*;
import mofo.com.pestscout.scouting.model.SpeciesCode;
import mofo.com.pestscout.scouting.service.ScoutingImageAnalysisService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ScoutingImageAnalysisController.class)
@AutoConfigureMockMvc(addFilters = false)
class ScoutingImageAnalysisControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ScoutingImageAnalysisService imageAnalysisService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void runsPhotoAnalysis() throws Exception {
        UUID farmId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();

        when(imageAnalysisService.analyzePhoto(farmId, photoId)).thenReturn(buildAnalysisResponse(farmId, photoId));

        mockMvc.perform(post("/api/scouting/photos/{photoId}/analysis", photoId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RunPhotoAnalysisRequest(farmId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photoId").value(photoId.toString()))
                .andExpect(jsonPath("$.predictedSpeciesCode").value("THRIPS"));
    }

    @Test
    void reviewsPhotoAnalysis() throws Exception {
        UUID farmId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();
        PhotoAnalysisResponse reviewedResponse = new PhotoAnalysisResponse(
                farmId,
                photoId,
                "SCOUT_HANDHELD",
                "heuristic-local-v1",
                "heuristic-local-v1",
                "Most likely thrips based on photo metadata and recent session observations.",
                false,
                "CORRECTED",
                "THRIPS",
                "Thrips",
                "PEST",
                0.91d,
                "WHITEFLIES",
                "Whiteflies",
                "PEST",
                "Corrected by manager",
                LocalDateTime.now(),
                LocalDateTime.now(),
                "Mia Manager",
                List.of(new PhotoAnalysisCandidate("THRIPS", "Thrips", "PEST", 0.91d, "Linked observation")),
                new AiAnalysisSnapshot(
                        "heuristic-local-v1",
                        "heuristic-local-v1",
                        "Most likely thrips based on photo metadata and recent session observations.",
                        "THRIPS",
                        "Thrips",
                        "PEST",
                        0.91d,
                        List.of(new PhotoAnalysisCandidate("THRIPS", "Thrips", "PEST", 0.91d, "Linked observation"))
                ),
                new ManualAnalysisSnapshot(
                        "CORRECTED",
                        "WHITEFLIES",
                        "Whiteflies",
                        "PEST",
                        "Corrected by manager",
                        LocalDateTime.now(),
                        "Mia Manager"
                ),
                new AnalysisComparison("CATEGORY_MATCH", false, true)
        );

        when(imageAnalysisService.reviewPhotoAnalysis(photoId, new PhotoAnalysisReviewRequest(farmId, SpeciesCode.WHITEFLIES, "Corrected by manager")))
                .thenReturn(reviewedResponse);

        mockMvc.perform(put("/api/scouting/photos/{photoId}/analysis/review", photoId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PhotoAnalysisReviewRequest(
                                farmId,
                                SpeciesCode.WHITEFLIES,
                                "Corrected by manager"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewStatus").value("CORRECTED"))
                .andExpect(jsonPath("$.reviewedSpeciesCode").value("WHITEFLIES"));
    }

    @Test
    void returnsAccuracySummary() throws Exception {
        UUID farmId = UUID.randomUUID();

        when(imageAnalysisService.getAccuracy(farmId)).thenReturn(new PhotoAnalysisAccuracyResponse(
                farmId,
                "heuristic-local-v1",
                "heuristic-local-v1",
                8,
                3,
                5,
                4,
                1,
                0.80d,
                0.77d,
                List.of(new PhotoAnalysisAccuracyBySpecies("THRIPS", "Thrips", 3, 2, 0.67d))
        ));

        mockMvc.perform(get("/api/scouting/photos/analysis/accuracy")
                        .param("farmId", farmId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewedCount").value(5))
                .andExpect(jsonPath("$.accuracyRate").value(0.8));
    }

    private PhotoAnalysisResponse buildAnalysisResponse(UUID farmId, UUID photoId) {
        return new PhotoAnalysisResponse(
                farmId,
                photoId,
                "SCOUT_HANDHELD",
                "heuristic-local-v1",
                "heuristic-local-v1",
                "Most likely thrips based on photo metadata and recent session observations.",
                false,
                "PENDING_REVIEW",
                "THRIPS",
                "Thrips",
                "PEST",
                0.91d,
                null,
                null,
                null,
                null,
                LocalDateTime.now(),
                null,
                null,
                List.of(new PhotoAnalysisCandidate("THRIPS", "Thrips", "PEST", 0.91d, "Linked observation")),
                new AiAnalysisSnapshot(
                        "heuristic-local-v1",
                        "heuristic-local-v1",
                        "Most likely thrips based on photo metadata and recent session observations.",
                        "THRIPS",
                        "Thrips",
                        "PEST",
                        0.91d,
                        List.of(new PhotoAnalysisCandidate("THRIPS", "Thrips", "PEST", 0.91d, "Linked observation"))
                ),
                new ManualAnalysisSnapshot(
                        "PENDING_REVIEW",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                ),
                new AnalysisComparison("PENDING_MANUAL_REVIEW", false, false)
        );
    }
}
