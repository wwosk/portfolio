package org.example.mypaintings.reservation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.example.mypaintings.exception.access.UnauthorizedAccessException;
import org.example.mypaintings.exception.painting.IllegalStateTransitionException;
import org.example.mypaintings.painting.Painting;
import org.example.mypaintings.painting.PaintingQueryService;
import org.example.mypaintings.painting.event.PaintingArchivedEvent;
import org.example.mypaintings.painting.placement.PaintingPlacement;
import org.example.mypaintings.painting.placement.PaintingPlacementQueryService;
import org.example.mypaintings.painting.placement.event.PaintingReturnedToArtistEvent;
import org.example.mypaintings.reservation.event.ReservationAcceptedEvent;
import org.example.mypaintings.reservation.event.ReservationRevokedEvent;
import org.example.mypaintings.user.User;
import org.example.mypaintings.user.UserQueryService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationService {
    private final ReservationOfferRepository reservationOfferRepository;
    private final UserQueryService userQueryService;
    private final PaintingQueryService paintingQueryService;
    private final ApplicationEventPublisher eventPublisher;
    private final PaintingPlacementQueryService placementQueryService;

    @Transactional
    @EventListener
    public void handlePaintingDeleted(PaintingArchivedEvent event) {
        Painting painting = paintingQueryService.getPaintingWithLockOrThrow(event.paintingId());
        archiveOffersForPainting(painting);
    }

    @EventListener
    @Transactional
    public void handlePaintingReturnedToArtist(PaintingReturnedToArtistEvent event) {
        Painting painting = paintingQueryService.getPaintingWithLockOrThrow(event.paintingId());
        archiveOffersForPainting(painting);
        log.info("Archived all reservation offers for painting ID: {} due to return to artist", event.paintingId());
    }

    @Transactional
    public void createReservationOffer(long hotelId, long paintingId) {
        log.info("Hotel ID: {} is creating a reservation offer for painting ID: {}", hotelId, paintingId);

        User hotel = userQueryService.getValidHotelOrThrow(hotelId);
        Painting painting = paintingQueryService.getPaintingWithLockOrThrow(paintingId);

        if (painting.isArchived()) {
            log.warn("Attempt to reserve an archived painting ID: {}", paintingId);
            throw new IllegalStateException("Cannot reserve an archived painting");
        }

        if (reservationOfferRepository.existsByPaintingIdAndStatus(paintingId, ReservationStatus.ACCEPTED)) {
            log.warn("Painting ID: {} already has an accepted reservation", paintingId);
            throw new IllegalStateTransitionException(
                    ReservationStatus.class,
                    ReservationStatus.ACCEPTED,
                    ReservationStatus.PENDING);
        }

        Optional<ReservationOffer> existing =
                reservationOfferRepository.findByHotelIdAndPaintingId(hotelId, paintingId);

        if (existing.isPresent()) {
            ReservationOffer offer = existing.get();
            log.info("Hotel ID: {} already has an offer ID: {} for painting ID: {}. Retrying.",
                    hotelId, offer.getId(), paintingId);
            if (offer.getStatus() == ReservationStatus.PENDING) {
                log.info("Offer ID: {} is already PENDING – nothing to do.", offer.getId());
                return;
            }
            offer.retryCreatingOffer();
            log.info("Successfully retried reservation offer ID: {} for painting ID: {}",
                    offer.getId(), paintingId);
            return;
        }

        ReservationOffer offer = ReservationOffer.builder()
                .hotel(hotel)
                .painting(painting)
                .offerDate(Instant.now())
                .status(ReservationStatus.PENDING)
                .build();

        reservationOfferRepository.save(offer);
        log.info("Successfully created reservation offer ID: {} for painting ID: {}",
                offer.getId(), paintingId);
    }

    @Transactional
    public void acceptReservationOffer(long artistId, long reservationId) {
        log.info("Artist ID: {} is accepting reservation offer ID: {}", artistId, reservationId);
        ReservationOffer offerToAccept = getVerifiedOffer(artistId, reservationId);

        Painting painting = paintingQueryService.getPaintingWithLockOrThrow(offerToAccept.getPainting().getId());

        if (reservationOfferRepository.existsByPaintingIdAndStatus(painting.getId(), ReservationStatus.ACCEPTED)) {
            log.warn("Painting ID: {} already has another accepted reservation", painting.getId());
            throw new IllegalStateTransitionException(ReservationStatus.class, ReservationStatus.ACCEPTED, ReservationStatus.ACCEPTED);
        }

        reservationOfferRepository.rejectOtherOffers(painting, reservationId);
        offerToAccept.accept();
        log.info("Accepted reservation offer ID: {} and rejected the others", reservationId);

        eventPublisher.publishEvent(new ReservationAcceptedEvent(reservationId, painting.getId(), offerToAccept.getHotel().getId()));
    }

    @Transactional
    public void rejectReservationOffer(long artistId, long reservationId) {
        log.info("Artist ID: {} is rejecting reservation offer ID: {}", artistId, reservationId);
        ReservationOffer offer = getVerifiedOffer(artistId, reservationId);
        offer.reject();
        log.info("Rejected reservation offer ID: {}", reservationId);
    }

    @Transactional
    public void revokeReservation(long userId, long reservationId) {
        log.info("User ID: {} is trying to revoke reservation ID: {}", userId, reservationId);

        ReservationOffer offer = reservationOfferRepository.getOrThrow(reservationId);
        Painting painting = offer.getPainting();

        boolean isArtist = painting.isOwnedBy(userId);
        boolean isHotel = offer.getHotel().getId().equals(userId);
        if (!isArtist && !isHotel) {
            log.warn("Unauthorized: User ID: {} does not have permissions to revoke reservation ID: {}", userId, reservationId);
            throw new UnauthorizedAccessException("No permissions to revoke the reservation.");
        }

        PaintingPlacement placement = placementQueryService.getActivePlacement(painting.getId());

        if (! placement.getPlacementStatus().isInStateForReservationRevoke()) {
            log.warn("Cannot revoke reservation ID: {} – painting ID: {} is in status: {}", reservationId, painting.getId(), placement.getPlacementStatus());
            throw new IllegalStateException("Cannot revoke reservation – the painting has already left the delivery stage.");
        }

        offer.revoke();
        eventPublisher.publishEvent(new ReservationRevokedEvent(reservationId, painting.getId()));

        log.info("Successfully revoked reservation ID: {} and cancelled the exposure cycle", reservationId);
    }

    @Transactional
    public void cancelReservationOffer(long hotelId, long reservationId) {
        log.info("Hotel ID: {} is cancelling its reservation offer ID: {}", hotelId, reservationId);

        ReservationOffer offer = reservationOfferRepository.getOrThrow(reservationId);

        if (!offer.getHotel().getId().equals(hotelId)) {
            log.warn("Unauthorized: Hotel ID: {} tried to cancel someone else's reservation offer ID: {}", hotelId, reservationId);
            throw new UnauthorizedAccessException("You can only cancel your own reservation offers.");
        }

        offer.cancel();
        log.info("Successfully cancelled reservation offer ID: {}", reservationId);
    }

    private ReservationOffer getVerifiedOffer(long artistId, long reservationId) {
        ReservationOffer offer = reservationOfferRepository.getOrThrow(reservationId);
        if (!offer.getPainting().isOwnedBy(artistId)) {
            log.warn("Unauthorized: Painting ID: {} does not belong to artist ID: {}", offer.getPainting().getId(), artistId);
            throw new UnauthorizedAccessException("The painting does not belong to this artist.");
        }
        return offer;
    }

    @Transactional
    public void archiveOffersForPainting(Painting painting) {
        List<ReservationOffer> offers = reservationOfferRepository.findAllByPainting(painting);
        for (ReservationOffer offer : offers) {
            offer.archive();
        }
        log.info("Archived all reservation offers for painting ID: {}", painting.getId());
    }








}
