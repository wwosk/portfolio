package org.example.mypaintings.geolocation;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
public class ArtistMovementAsyncService {

    private final ArtistMovementRetryService retryService;

    @Async("locationTaskExecutor")
    public void processAsync(Long artistId, double searchRadius) {
        retryService.processWithRetry(artistId, searchRadius);
    }
}