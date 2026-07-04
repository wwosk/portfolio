package org.example.mypaintings.geolocation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.mypaintings.user.UserQueryService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.List;


@Component
@RequiredArgsConstructor
@Slf4j
public class LocationRefreshScheduler {

    private final UserQueryService userQueryService;

    @Value("${app.geolocation.search-radius:800.0}")
    private double searchRadius;
    private final ArtistMovementAsyncService artistMovementAsyncService;

    @Scheduled(fixedRate = 3600000)
    public void refreshProximityDataMultiThreaded() {
        List<Long> movedArtistIds = userQueryService.getArtistIdsWithChangedLocation();
        for (Long id : movedArtistIds) {
            artistMovementAsyncService.processAsync(id, searchRadius);
        }
    }
}