package org.example.mypaintings.reservation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.mypaintings.exception.notfound.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReservationQueryService {

    private final ReservationOfferRepository reservationOfferRepository;

    public ReservationOffer getValidOrThrow(long offerId) {
        log.debug("Fetching reservation offer ID: {}", offerId);
        return reservationOfferRepository.findById(offerId)
                .orElseThrow(() -> new ResourceNotFoundException(ReservationOffer.class, "Id: " + offerId));
    }

    public boolean hasActiveReservation(long paintingId) {
        log.debug("Checking if painting ID: {} has an active accepted reservation", paintingId);
        return reservationOfferRepository.existsByPaintingIdAndStatus(paintingId, ReservationStatus.ACCEPTED);
    }

}