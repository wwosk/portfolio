package org.example.mypaintings.geolocation;



import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.mypaintings.user.UserRole;
import org.example.mypaintings.user.event.UserCreatedEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import static org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserCreatedGeolocationListener {

    private final GeolocationService geolocationService;

    @Value("${app.geolocation.search-radius:800.0}")
    private double searchRadius;

    @TransactionalEventListener(phase = AFTER_COMMIT)
    public void onUserCreated(UserCreatedEvent event) {
        if (event.role() != UserRole.HOTEL) {
            return;
        }
        log.info("Triggering full geolocation scan for new hotel ID: {}", event.userId());
        geolocationService.fullScanForNewHotel(event.userId(),searchRadius);
    }
}