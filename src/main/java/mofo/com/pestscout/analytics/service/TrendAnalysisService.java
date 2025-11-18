package mofo.com.pestscout.analytics.service;

import lombok.RequiredArgsConstructor;
import mofo.com.pestscout.analytics.dto.PestTrendResponse;
import mofo.com.pestscout.analytics.dto.TrendPointDto;
import mofo.com.pestscout.farm.repository.ScoutingObservationRepository;
import mofo.com.pestscout.farm.repository.ScoutingSessionRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class TrendAnalysisService {

    private final ScoutingSessionRepository sessionRepo;
    private final ScoutingObservationRepository obsRepo;

    public PestTrendResponse getPestTrend(
            UUID farmId,
            String speciesCode,
            LocalDate from,
            LocalDate to
    ) {
        var sessions = sessionRepo.findByFarmIdAndSessionDateBetween(farmId, from, to);
        Map<LocalDate, Integer> dateToSeverity = new TreeMap<>();

        for (var session : sessions) {
            var observations = obsRepo.findBySessionIdIn(List.of(session.getId()));

            int total = observations.stream()
                    .filter(o -> o.getSpeciesCode().equalsIgnoreCase(speciesCode))
                    .mapToInt(o -> Optional.ofNullable(o.getCount()).orElse(0))
                    .sum();

            dateToSeverity.merge(session.getSessionDate(), total, Integer::sum);
        }

        List<TrendPointDto> points = dateToSeverity.entrySet().stream()
                .map(e -> new TrendPointDto(e.getKey(), e.getValue()))
                .toList();

        return new PestTrendResponse(farmId, speciesCode, points);
    }
}
