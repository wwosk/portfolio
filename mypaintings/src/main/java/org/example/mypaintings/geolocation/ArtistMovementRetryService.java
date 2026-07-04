package org.example.mypaintings.geolocation;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
public class ArtistMovementRetryService {

    private final ArtistMovementProcessor artistMovementProcessor;

    @Retryable(
            retryFor = {
                    OptimisticLockingFailureException.class,
                    CannotAcquireLockException.class
            },
            backoff = @Backoff(delay = 200, multiplier = 2)
    )
    public void processWithRetry(Long artistId, double searchRadius) {
        artistMovementProcessor.processArtistMovement(artistId, searchRadius);
    }
}