package org.example.mypaintings.painting.placement;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.mypaintings.exception.access.UnauthorizedAccessException;
import org.example.mypaintings.painting.Painting;
import org.example.mypaintings.painting.PaintingQueryService;

import org.example.mypaintings.painting.placement.event.PaintingReturnedToArtistEvent;
import org.example.mypaintings.painting.placement.event.PlacementStartedEvent;
import org.example.mypaintings.reservation.ReservationOffer;
import org.example.mypaintings.reservation.ReservationQueryService;
import org.example.mypaintings.reservation.event.ReservationAcceptedEvent;
import org.example.mypaintings.reservation.event.ReservationRevokedEvent;
import org.example.mypaintings.sale.event.PaintingSoldEvent;
import org.example.mypaintings.user.User;
import org.example.mypaintings.user.UserQueryService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Slf4j
@Service
@RequiredArgsConstructor

public class PaintingPlacementService {
    private final PaintingPlacementRepository paintingPlacementRepository;
    private final UserQueryService userQueryService;
    private final ReturnDeliveryInfoRepository returnDeliveryInfoRepository;
    private final PaintingQueryService paintingQueryService;
    private final ApplicationEventPublisher eventPublisher;
    private final ReservationQueryService reservationQueryService;
    private final PaintingPlacementQueryService paintingPlacementQueryService;


    @EventListener
    @Transactional
    public void handlePaintingSold(PaintingSoldEvent event) {
       if(event.deliveryNeeded()) {
           prepareForClientShipping(event.paintingId());
       }
       else {
           prepareForClientReception(event.paintingId());
       }
    }

    @EventListener
    @Transactional
    public void handleReservationAccepted(ReservationAcceptedEvent event) {
        ReservationOffer acceptedOffer = reservationQueryService.getValidOrThrow(event.reservationId());
        startPlacementCycle(event.paintingId(), event.hotelId(), acceptedOffer);
    }


    @EventListener
    @Transactional
    public void handleReservationRevoked(ReservationRevokedEvent event) {
        PaintingPlacement placement = paintingPlacementQueryService.getActivePlacement(event.paintingId());
        placement.transitionTo(PlacementStatus.REVOKED_RESERVATION);
        log.info("Cancelled placement cycle and archived for painting ID: {} due to reservation revocation", event.paintingId());
    }


    @Transactional
    public void startPlacementCycle(long paintingId, long hotelId, ReservationOffer acceptedOffer) {
        log.info("Starting a new placement cycle for painting ID: {} in hotel ID: {}", paintingId, hotelId);

        Painting painting = paintingQueryService.getPaintingWithLockOrThrow(paintingId);

        if (paintingPlacementRepository.existsByPaintingIdAndArchivedFalse(paintingId)) {
            log.warn("Attempt to start a cycle for painting ID: {}, which is already in an active placement!", paintingId);
            throw new IllegalStateException("The painting is already in an active cycle!");
        }

        User hotel = userQueryService.getValidHotelOrThrow(hotelId);

        PaintingPlacement placement = paintingPlacementRepository.save(PaintingPlacement.startCycle(painting, hotel));
        log.info("Saved new placement cycle ID: {} for painting ID: {}", placement.getId(), paintingId);


        eventPublisher.publishEvent(new PlacementStartedEvent(paintingId, hotelId, acceptedOffer.getId()));
    }

    @Transactional
    public void revokeByArtist(long paintingId, long artistId, boolean deliveryNeeded) {
        log.info("Artist ID: {} is revoking painting ID: {} (delivery needed: {})", artistId, paintingId, deliveryNeeded);

        PaintingPlacement placement = paintingPlacementQueryService.getActivePlacement(paintingId);

        if (!placement.getPainting().isOwnedBy(artistId)) {
            log.warn("Unauthorized: User ID: {} attempted to revoke a painting ID: {} that they do not own", artistId, paintingId);
            throw new UnauthorizedAccessException("Only the author can revoke the painting");
        }

        if (deliveryNeeded) {
            ReturnDeliveryInfo returnDeliveryInfo = new ReturnDeliveryInfo();
            returnDeliveryInfo.setFullAddress(placement.getPainting().getArtist().getFullAddress());
            returnDeliveryInfo.setPlacement(placement);
            returnDeliveryInfoRepository.save(returnDeliveryInfo);
            log.info("Created new ReturnDeliveryInfo for painting ID: {}", paintingId);

            placement.transitionTo(PlacementStatus.WAITING_FOR_SHIPPING_TO_ARTIST);
            log.info("Set status to waiting for shipping to artist for painting ID: {}", paintingId);
        } else {
            placement.transitionTo(PlacementStatus.WAITING_FOR_RECEPTION_BY_ARTIST);
            log.info("Set status to waiting for reception by artist for painting ID: {}", paintingId);
        }


    }

    @Transactional
    public void updateHotelAction(long paintingId, long hotelId, PlacementStatus status) {
        log.info("Hotel ID: {} is updating placement status for painting ID: {} to: {}", hotelId, paintingId, status);

        PaintingPlacement placement = paintingPlacementQueryService.getActivePlacement(paintingId);
        placement.verifyHotelAccess(hotelId);

        if (status.isSoldAndNotObtainedStatus()) {
            log.warn("Attempt to change status to sold for painting ID: {}, which has no associated sale in the database!", paintingId);
            throw new IllegalStateException("The painting has not been sold!");
        }

        placement.transitionTo(status);
        log.debug("Successfully changed placement status for painting ID: {} to {}", paintingId, status);
    }

    @Transactional
    public void addNote(long hotelId, long paintingId, AddNoteRequest noteRequest) {
        log.debug("Hotel ID: {} is adding a note to the placement of painting ID: {}", hotelId, paintingId);
        PaintingPlacement placement = paintingPlacementQueryService.getActivePlacement(paintingId);
        placement.verifyHotelAccess(hotelId);
        placement.setNote(noteRequest.note());
    }

    @Transactional
    public void exhibitPainting(long paintingId, long hotelId) {
        updateHotelAction(paintingId, hotelId, PlacementStatus.EXHIBITED);
    }

    @Transactional
    public void confirmReceptionByClient(long paintingId, long hotelId) {
        updateHotelAction(paintingId, hotelId, PlacementStatus.RECEIVED_BY_CLIENT);
    }

    @Transactional
    public void sendToClient(long paintingId, long hotelId) {
        updateHotelAction(paintingId, hotelId, PlacementStatus.SENT_BY_HOTEL_TO_CLIENT);
    }

    @Transactional
    public void returnToArtist(long paintingId, long hotelId) {
        updateHotelAction(paintingId, hotelId, PlacementStatus.RETURNED_TO_ARTIST);
        eventPublisher.publishEvent(new PaintingReturnedToArtistEvent(paintingId));
    }

    @Transactional
    public void sendToArtist(long paintingId, long hotelId) {
        updateHotelAction(paintingId, hotelId, PlacementStatus.SENT_BY_HOTEL_TO_ARTIST);
    }

    @Transactional
    public void prepareForClientShipping(long paintingId) {
        log.info("Preparing for shipping to client for painting ID: {}", paintingId);
        PaintingPlacement placement = paintingPlacementQueryService.getActivePlacement(paintingId);
        placement.transitionTo(PlacementStatus.WAITING_FOR_SHIPPING_TO_CLIENT);
    }

    @Transactional
    public void prepareForClientReception(long paintingId) {
        log.info("Preparing for client reception for painting ID: {}", paintingId);
        PaintingPlacement placement = paintingPlacementQueryService.getActivePlacement(paintingId);
        placement.transitionTo(PlacementStatus.WAITING_FOR_RECEPTION_BY_CLIENT);
    }







}