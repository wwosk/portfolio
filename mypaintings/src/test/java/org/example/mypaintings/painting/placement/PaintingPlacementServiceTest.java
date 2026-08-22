package org.example.mypaintings.painting.placement;

import org.example.mypaintings.exception.access.UnauthorizedAccessException;
import org.example.mypaintings.exception.notfound.ResourceNotFoundException;
import org.example.mypaintings.exception.painting.IllegalStateTransitionException;
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
import org.example.mypaintings.user.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaintingPlacementService Test Suite")
class PaintingPlacementServiceTest {

    @Mock
    private PaintingPlacementRepository paintingPlacementRepository;
    @Mock
    private UserQueryService userQueryService;
    @Mock
    private ReturnDeliveryInfoRepository returnDeliveryInfoRepository;
    @Mock
    private PaintingQueryService paintingQueryService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private ReservationQueryService reservationQueryService;
    @Mock
    private PaintingPlacementQueryService paintingPlacementQueryService;

    @InjectMocks
    private PaintingPlacementService service;

    // ---------------------------------------------------------------------
    // Fixtures
    // ---------------------------------------------------------------------

    private static Painting painting(long id, long artistId) {
        Painting p = new Painting();
        User artist = new User(UserRole.ARTIST, "artist" + artistId, "addr", 0.0, 0.0);
        ReflectionTestUtils.setField(artist, "id", artistId);
        ReflectionTestUtils.setField(p, "id", id);
        ReflectionTestUtils.setField(p, "artist", artist);
        return p;
    }

    private static User hotel(long id) {
        User h = new User(UserRole.HOTEL, "hotel" + id, "addr", 0.0, 0.0);
        ReflectionTestUtils.setField(h, "id", id);
        return h;
    }

    private static PaintingPlacement placement(long id, Painting painting, User hotel, PlacementStatus status) {
        PaintingPlacement p = PaintingPlacement.startCycle(painting, hotel);
        ReflectionTestUtils.setField(p, "id", id);
        ReflectionTestUtils.setField(p, "placementStatus", status);
        return p;
    }

    // ---------------------------------------------------------------------
    // startPlacementCycle
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("startPlacementCycle")
    class StartPlacementCycle {

        @Test
        @DisplayName("starts cycle successfully and publishes PlacementStartedEvent")
        void startsCycleAndPublishesPlacementStartedEvent() {
            // given
            long paintingId = 1L, hotelId = 2L, offerId = 3L;
            Painting painting = painting(paintingId, 99L);
            User hotel = hotel(hotelId);
            ReservationOffer offer = mock(ReservationOffer.class);
            when(offer.getId()).thenReturn(offerId);

            when(paintingQueryService.getPaintingWithLockOrThrow(paintingId)).thenReturn(painting);
            when(paintingPlacementRepository.existsByPaintingIdAndArchivedFalse(paintingId)).thenReturn(false);
            when(userQueryService.getValidHotelOrThrow(hotelId)).thenReturn(hotel);
            when(paintingPlacementRepository.save(any())).thenAnswer(inv -> {
                PaintingPlacement p = inv.getArgument(0);
                ReflectionTestUtils.setField(p, "id", 10L);
                return p;
            });

            // when
            service.startPlacementCycle(paintingId, hotelId, offer);

            // then
            verify(paintingPlacementRepository).save(argThat(p ->
                    p.getPainting() == painting
                            && p.getHotel() == hotel
                            && p.getPlacementStatus() == PlacementStatus.IN_DELIVERY_TO_HOTEL
                            && p.getNote() != null && p.getNote().isEmpty()
                            && !p.isArchived()));
            verify(eventPublisher).publishEvent(new PlacementStartedEvent(paintingId, hotelId, offerId));
        }

        @Test
        @DisplayName("rejects when an active cycle already exists")
        void rejectsWhenActiveCycleAlreadyExists() {
            // given
            long paintingId = 1L;
            when(paintingQueryService.getPaintingWithLockOrThrow(paintingId))
                    .thenReturn(painting(paintingId, 99L));
            when(paintingPlacementRepository.existsByPaintingIdAndArchivedFalse(paintingId)).thenReturn(true);

            // when & then
            assertThatThrownBy(() -> service.startPlacementCycle(paintingId, 2L, mock(ReservationOffer.class)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already in an active cycle");

            verify(paintingPlacementRepository, never()).save(any());
            verifyNoInteractions(userQueryService, eventPublisher);
        }

        @Test
        @DisplayName("propagates exception when painting cannot be loaded")
        void propagatesWhenPaintingCannotBeLoaded() {
            // given
            long paintingId = 1L;
            when(paintingQueryService.getPaintingWithLockOrThrow(paintingId))
                    .thenThrow(new ResourceNotFoundException(Painting.class, "id" + paintingId));

            // when & then
            assertThatThrownBy(() -> service.startPlacementCycle(paintingId, 2L, mock(ReservationOffer.class)))
                    .isInstanceOf(ResourceNotFoundException.class);

            verifyNoInteractions(paintingPlacementRepository, userQueryService, eventPublisher);
        }

        @Test
        @DisplayName("does not publish event when hotel lookup fails")
        void doesNotPublishEventWhenHotelLookupFails() {
            // given
            long paintingId = 1L, hotelId = 2L;
            when(paintingQueryService.getPaintingWithLockOrThrow(paintingId))
                    .thenReturn(painting(paintingId, 99L));
            when(paintingPlacementRepository.existsByPaintingIdAndArchivedFalse(paintingId)).thenReturn(false);
            when(userQueryService.getValidHotelOrThrow(hotelId))
                    .thenThrow(new ResourceNotFoundException(User.class, "id" + hotelId));

            // when & then
            assertThatThrownBy(() -> service.startPlacementCycle(paintingId, hotelId, mock(ReservationOffer.class)))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(paintingPlacementRepository, never()).save(any());
            verifyNoInteractions(eventPublisher);
        }
    }

    // ---------------------------------------------------------------------
    // handlePaintingSold
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("handlePaintingSold")
    class HandlePaintingSold {

        @Test
        @DisplayName("prepares for shipping to client when delivery is needed")
        void deliveryNeeded_preparesForShippingToClient() {
            // given
            long paintingId = 1L;
            PaintingPlacement placement = placement(5L, painting(paintingId, 9L), hotel(2L), PlacementStatus.EXHIBITED);
            when(paintingPlacementQueryService.getActivePlacement(paintingId)).thenReturn(placement);

            // when
            service.handlePaintingSold(new PaintingSoldEvent(paintingId, true));

            // then
            assertThat(placement.getPlacementStatus())
                    .isEqualTo(PlacementStatus.WAITING_FOR_SHIPPING_TO_CLIENT);
            assertThat(placement.isArchived()).isFalse();
        }

        @Test
        @DisplayName("prepares for reception by client when delivery is not needed")
        void noDelivery_preparesForReceptionByClient() {
            // given
            long paintingId = 1L;
            PaintingPlacement placement = placement(5L, painting(paintingId, 9L), hotel(2L), PlacementStatus.EXHIBITED);
            when(paintingPlacementQueryService.getActivePlacement(paintingId)).thenReturn(placement);

            // when
            service.handlePaintingSold(new PaintingSoldEvent(paintingId, false));

            // then
            assertThat(placement.getPlacementStatus())
                    .isEqualTo(PlacementStatus.WAITING_FOR_RECEPTION_BY_CLIENT);
            assertThat(placement.isArchived()).isFalse();
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when no active placement exists")
        void throwsWhenNoActivePlacementExists() {
            // given
            long paintingId = 1L;
            when(paintingPlacementQueryService.getActivePlacement(paintingId))
                    .thenThrow(new ResourceNotFoundException(PaintingPlacement.class, "id" + paintingId));

            // when & then
            assertThatThrownBy(() -> service.handlePaintingSold(new PaintingSoldEvent(paintingId, true)))
                    .isInstanceOf(ResourceNotFoundException.class);

            verifyNoInteractions(eventPublisher);
        }
    }

    // ---------------------------------------------------------------------
    // handleReservationAccepted
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("handleReservationAccepted")
    class HandleReservationAccepted {

        @Test
        @DisplayName("fetches offer and delegates to startPlacementCycle")
        void fetchesOfferAndDelegatesToStartPlacementCycle() {
            // given
            long reservationId = 7L, paintingId = 1L, hotelId = 2L;
            ReservationOffer offer = mock(ReservationOffer.class);
            when(reservationQueryService.getValidOrThrow(reservationId)).thenReturn(offer);

            PaintingPlacementService spy = spy(service);
            doNothing().when(spy).startPlacementCycle(paintingId, hotelId, offer);

            // when
            spy.handleReservationAccepted(new ReservationAcceptedEvent(reservationId, paintingId, hotelId));

            // then
            verify(reservationQueryService).getValidOrThrow(reservationId);
            verify(spy).startPlacementCycle(paintingId, hotelId, offer);
        }

        @Test
        @DisplayName("does not start cycle when offer is invalid")
        void doesNotStartCycleWhenOfferIsInvalid() {
            // given
            long reservationId = 7L, paintingId = 1L, hotelId = 2L;
            when(reservationQueryService.getValidOrThrow(reservationId))
                    .thenThrow(new ResourceNotFoundException(ReservationOffer.class, "id" + reservationId));

            PaintingPlacementService spy = spy(service);

            // when & then
            assertThatThrownBy(() ->
                    spy.handleReservationAccepted(new ReservationAcceptedEvent(reservationId, paintingId, hotelId)))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(spy, never()).startPlacementCycle(anyLong(), anyLong(), any());
        }
    }

    // ---------------------------------------------------------------------
    // handleReservationRevoked
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("handleReservationRevoked")
    class HandleReservationRevoked {

        @Test
        @DisplayName("transitions active placement to revoked reservation status and archives it")
        void transitionsActivePlacementToRevokedReservation() {
            // given
            long paintingId = 1L;
            PaintingPlacement placement = placement(
                    5L, painting(paintingId, 9L), hotel(2L), PlacementStatus.IN_DELIVERY_TO_HOTEL);
            when(paintingPlacementQueryService.getActivePlacement(paintingId)).thenReturn(placement);

            // when
            service.handleReservationRevoked(new ReservationRevokedEvent(1, paintingId));

            // then
            assertThat(placement.getPlacementStatus()).isEqualTo(PlacementStatus.REVOKED_RESERVATION);
            assertThat(placement.isArchived()).isTrue();
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when no active placement exists")
        void throwsWhenNoActivePlacementExists() {
            // given
            long paintingId = 1L;
            when(paintingPlacementQueryService.getActivePlacement(paintingId))
                    .thenThrow(new ResourceNotFoundException(PaintingPlacement.class, "id" + paintingId));

            // when & then
            assertThatThrownBy(() ->
                    service.handleReservationRevoked(new ReservationRevokedEvent(1, paintingId)))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("does not publish any event when reservation is revoked")
        void doesNotPublishAnyEvent() {
            // given
            long paintingId = 1L;
            PaintingPlacement placement = placement(
                    5L, painting(paintingId, 9L), hotel(2L), PlacementStatus.IN_DELIVERY_TO_HOTEL);
            when(paintingPlacementQueryService.getActivePlacement(paintingId)).thenReturn(placement);

            // when
            service.handleReservationRevoked(new ReservationRevokedEvent(1, paintingId));

            // then
            verifyNoInteractions(eventPublisher);
        }
    }

    // ---------------------------------------------------------------------
    // revokeByArtist
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("revokeByArtist")
    class RevokeByArtist {

        @Test
        @DisplayName("throws UnauthorizedAccessException when artist is not the owner")
        void throwsWhenArtistIsNotOwner() {
            // given
            long paintingId = 1L, artistId = 42L;
            PaintingPlacement placement = placement(
                    5L, painting(paintingId, 99L), hotel(2L), PlacementStatus.EXHIBITED);
            when(paintingPlacementQueryService.getActivePlacement(paintingId)).thenReturn(placement);

            // when & then
            assertThatThrownBy(() -> service.revokeByArtist(paintingId, artistId, false))
                    .isInstanceOf(UnauthorizedAccessException.class);

            assertThat(placement.getPlacementStatus()).isEqualTo(PlacementStatus.EXHIBITED);
            verifyNoInteractions(returnDeliveryInfoRepository, eventPublisher);
        }

        @Test
        @DisplayName("creates return delivery info and updates status when delivery is needed")
        void revokeByArtist_withDeliveryNeeded_createsReturnDeliveryInfoAndUpdatesStatus() {
            // given
            long paintingId = 1L;
            long artistId = 42L;

            Painting painting = painting(paintingId, artistId);
            PaintingPlacement placement = placement(5L, painting, hotel(2L), PlacementStatus.EXHIBITED);

            when(paintingPlacementQueryService.getActivePlacement(paintingId)).thenReturn(placement);

            // when
            service.revokeByArtist(paintingId, artistId, true);

            // then
            verify(returnDeliveryInfoRepository).save(argThat(info ->
                    info.getPlacement() == placement
                            && info.getFullAddress().equals(painting.getArtist().getFullAddress())
            ));

            assertThat(placement.getPlacementStatus())
                    .isEqualTo(PlacementStatus.WAITING_FOR_SHIPPING_TO_ARTIST);
            assertThat(placement.isArchived()).isFalse();
            verifyNoInteractions(eventPublisher);
        }

        @Test
        @DisplayName("transitions to waiting for reception by artist when delivery is not needed")
        void withoutDelivery_transitionsToWaitingForReceptionByArtist() {
            // given
            long paintingId = 1L, artistId = 42L;
            PaintingPlacement placement = placement(
                    5L, painting(paintingId, artistId), hotel(2L), PlacementStatus.EXHIBITED);
            when(paintingPlacementQueryService.getActivePlacement(paintingId)).thenReturn(placement);

            // when
            service.revokeByArtist(paintingId, artistId, false);

            // then
            verifyNoInteractions(returnDeliveryInfoRepository, eventPublisher);
            assertThat(placement.getPlacementStatus())
                    .isEqualTo(PlacementStatus.WAITING_FOR_RECEPTION_BY_ARTIST);
            assertThat(placement.isArchived()).isFalse();
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when no active placement exists")
        void throwsWhenNoActivePlacementExists() {
            // given
            long paintingId = 1L, artistId = 42L;
            when(paintingPlacementQueryService.getActivePlacement(paintingId))
                    .thenThrow(new ResourceNotFoundException(PaintingPlacement.class, "id" + paintingId));

            // when & then
            assertThatThrownBy(() -> service.revokeByArtist(paintingId, artistId, false))
                    .isInstanceOf(ResourceNotFoundException.class);

            verifyNoInteractions(returnDeliveryInfoRepository, eventPublisher);
        }
    }

    // ---------------------------------------------------------------------
    // updateHotelAction
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("updateHotelAction")
    class UpdateHotelAction {

        @Test
        @DisplayName("throws UnauthorizedAccessException when hotel is not in possession")
        void throwsWhenHotelIsNotInPossession() {
            // given
            long paintingId = 1L, hotelId = 2L;
            PaintingPlacement placement = placement(
                    5L, painting(paintingId, 9L), hotel(3L), PlacementStatus.EXHIBITED);
            when(paintingPlacementQueryService.getActivePlacement(paintingId)).thenReturn(placement);

            // when & then
            assertThatThrownBy(() -> service.updateHotelAction(
                    paintingId, hotelId, PlacementStatus.WAITING_FOR_SHIPPING_TO_CLIENT))
                    .isInstanceOf(UnauthorizedAccessException.class);

            assertThat(placement.getPlacementStatus()).isEqualTo(PlacementStatus.EXHIBITED);
        }

        @Test
        @DisplayName("rejects sold status when painting has not been sold")
        void rejectsSoldStatusWithoutSale() {
            // given
            long paintingId = 1L, hotelId = 2L;
            PaintingPlacement placement = placement(
                    5L, painting(paintingId, 9L), hotel(hotelId), PlacementStatus.EXHIBITED);
            when(paintingPlacementQueryService.getActivePlacement(paintingId)).thenReturn(placement);

            // when & then
            assertThatThrownBy(() -> service.updateHotelAction(
                    paintingId, hotelId, PlacementStatus.WAITING_FOR_SHIPPING_TO_CLIENT))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("has not been sold");

            assertThat(placement.getPlacementStatus()).isEqualTo(PlacementStatus.EXHIBITED);
        }

        @Test
        @DisplayName("rejects illegal state transition")
        void rejectsIllegalTransition() {
            // given
            long paintingId = 1L, hotelId = 2L;
            PaintingPlacement placement = placement(
                    5L, painting(paintingId, 9L), hotel(hotelId), PlacementStatus.EXHIBITED);
            when(paintingPlacementQueryService.getActivePlacement(paintingId)).thenReturn(placement);

            // when & then
            assertThatThrownBy(() -> service.updateHotelAction(
                    paintingId, hotelId, PlacementStatus.RECEIVED_BY_CLIENT))
                    .isInstanceOf(IllegalStateTransitionException.class);

            assertThat(placement.getPlacementStatus()).isEqualTo(PlacementStatus.EXHIBITED);
            assertThat(placement.isArchived()).isFalse();
        }

        @Test
        @DisplayName("transitions to exhibited via exhibitPainting helper")
        void transitionsToExhibitedViaExhibitPainting() {
            // given
            long paintingId = 1L, hotelId = 2L;
            PaintingPlacement placement = placement(
                    5L, painting(paintingId, 9L), hotel(hotelId), PlacementStatus.IN_DELIVERY_TO_HOTEL);
            when(paintingPlacementQueryService.getActivePlacement(paintingId)).thenReturn(placement);

            // when
            service.exhibitPainting(paintingId, hotelId);

            // then
            assertThat(placement.getPlacementStatus()).isEqualTo(PlacementStatus.EXHIBITED);
        }

        @Test
        @DisplayName("rejects send to client because target status is a sold-unobtained status")
        void sendToClient_isRejectedBecauseTargetStatusIsSoldStatus() {
            // given
            long paintingId = 1L, hotelId = 2L;
            PaintingPlacement placement = placement(
                    5L, painting(paintingId, 9L), hotel(hotelId), PlacementStatus.WAITING_FOR_SHIPPING_TO_CLIENT);
            when(paintingPlacementQueryService.getActivePlacement(paintingId)).thenReturn(placement);

            // when & then
            assertThatThrownBy(() -> service.sendToClient(paintingId, hotelId))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("has not been sold");

            assertThat(placement.getPlacementStatus())
                    .isEqualTo(PlacementStatus.WAITING_FOR_SHIPPING_TO_CLIENT);
        }

        @Test
        @DisplayName("confirms reception by client, transitions status and archives placement")
        void confirmReceptionByClient_transitionsAndArchives() {
            // given
            long paintingId = 1L, hotelId = 2L;
            PaintingPlacement placement = placement(
                    5L, painting(paintingId, 9L), hotel(hotelId), PlacementStatus.SENT_BY_HOTEL_TO_CLIENT);
            when(paintingPlacementQueryService.getActivePlacement(paintingId)).thenReturn(placement);

            // when
            service.confirmReceptionByClient(paintingId, hotelId);

            // then
            assertThat(placement.getPlacementStatus()).isEqualTo(PlacementStatus.RECEIVED_BY_CLIENT);
            assertThat(placement.isArchived()).isTrue();
        }

        @Test
        @DisplayName("transitions to sent by hotel to artist")
        void sendToArtist_transitionsToSentByHotelToArtist() {
            // given
            long paintingId = 1L, hotelId = 2L;
            PaintingPlacement placement = placement(
                    5L, painting(paintingId, 9L), hotel(hotelId), PlacementStatus.WAITING_FOR_SHIPPING_TO_ARTIST);
            when(paintingPlacementQueryService.getActivePlacement(paintingId)).thenReturn(placement);

            // when
            service.sendToArtist(paintingId, hotelId);

            // then
            assertThat(placement.getPlacementStatus()).isEqualTo(PlacementStatus.SENT_BY_HOTEL_TO_ARTIST);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when no active placement exists")
        void throwsWhenNoActivePlacementExists() {
            // given
            long paintingId = 1L, hotelId = 2L;
            when(paintingPlacementQueryService.getActivePlacement(paintingId))
                    .thenThrow(new ResourceNotFoundException(PaintingPlacement.class, "id" + paintingId));

            // when & then
            assertThatThrownBy(() -> service.exhibitPainting(paintingId, hotelId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ---------------------------------------------------------------------
    // returnToArtist
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("returnToArtist")
    class ReturnToArtist {

        @Test
        @DisplayName("transitions to returned to artist, archives and publishes event")
        void transitionsToReturnedToArtist_archivesAndPublishesEvent() {
            // given
            long paintingId = 1L, hotelId = 2L;
            PaintingPlacement placement = placement(
                    5L, painting(paintingId, 9L), hotel(hotelId), PlacementStatus.SENT_BY_HOTEL_TO_ARTIST);
            when(paintingPlacementQueryService.getActivePlacement(paintingId)).thenReturn(placement);

            // when
            service.returnToArtist(paintingId, hotelId);

            // then
            assertThat(placement.getPlacementStatus()).isEqualTo(PlacementStatus.RETURNED_TO_ARTIST);
            assertThat(placement.isArchived()).isTrue();
            verify(eventPublisher).publishEvent(new PaintingReturnedToArtistEvent(paintingId));
        }

        @Test
        @DisplayName("publishes event even when returning from waiting for reception by artist status")
        void publishesEventEvenFromWaitingForReceptionByArtist() {
            // given
            long paintingId = 1L, hotelId = 2L;
            PaintingPlacement placement = placement(
                    5L, painting(paintingId, 9L), hotel(hotelId), PlacementStatus.WAITING_FOR_RECEPTION_BY_ARTIST);
            when(paintingPlacementQueryService.getActivePlacement(paintingId)).thenReturn(placement);

            // when
            service.returnToArtist(paintingId, hotelId);

            // then
            assertThat(placement.getPlacementStatus()).isEqualTo(PlacementStatus.RETURNED_TO_ARTIST);
            verify(eventPublisher).publishEvent(new PaintingReturnedToArtistEvent(paintingId));
        }

        @Test
        @DisplayName("does not publish event when transition fails")
        void doesNotPublishEventWhenTransitionFails() {
            // given
            long paintingId = 1L, hotelId = 2L;
            PaintingPlacement placement = placement(
                    5L, painting(paintingId, 9L), hotel(hotelId), PlacementStatus.EXHIBITED);
            when(paintingPlacementQueryService.getActivePlacement(paintingId)).thenReturn(placement);

            // when & then
            assertThatThrownBy(() -> service.returnToArtist(paintingId, hotelId))
                    .isInstanceOf(IllegalStateTransitionException.class);

            verifyNoInteractions(eventPublisher);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when no active placement exists")
        void throwsWhenNoActivePlacementExists() {
            // given
            long paintingId = 1L, hotelId = 2L;
            when(paintingPlacementQueryService.getActivePlacement(paintingId))
                    .thenThrow(new ResourceNotFoundException(PaintingPlacement.class, "id" + paintingId));

            // when & then
            assertThatThrownBy(() -> service.returnToArtist(paintingId, hotelId))
                    .isInstanceOf(ResourceNotFoundException.class);

            verifyNoInteractions(eventPublisher);
        }
    }

    // ---------------------------------------------------------------------
    // addNote
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("addNote")
    class AddNote {

        @Test
        @DisplayName("sets note successfully when hotel owns placement")
        void setsNoteWhenHotelOwnsPlacement() {
            // given
            long paintingId = 1L, hotelId = 2L;
            PaintingPlacement placement = placement(
                    5L, painting(paintingId, 9L), hotel(hotelId), PlacementStatus.EXHIBITED);
            when(paintingPlacementQueryService.getActivePlacement(paintingId)).thenReturn(placement);

            // when
            service.addNote(hotelId, paintingId, new AddNoteRequest("hello"));

            // then
            assertThat(placement.getNote()).isEqualTo("hello");
        }

        @Test
        @DisplayName("rejects foreign hotel and throws UnauthorizedAccessException")
        void rejectsForeignHotel() {
            // given
            long paintingId = 1L, hotelId = 2L;
            PaintingPlacement placement = placement(
                    5L, painting(paintingId, 9L), hotel(3L), PlacementStatus.EXHIBITED);
            when(paintingPlacementQueryService.getActivePlacement(paintingId)).thenReturn(placement);

            // when & then
            assertThatThrownBy(() -> service.addNote(hotelId, paintingId, new AddNoteRequest("x")))
                    .isInstanceOf(UnauthorizedAccessException.class);

            assertThat(placement.getNote()).isEmpty();
        }

        @Test
        @DisplayName("overwrites existing note successfully")
        void overwritesExistingNote() {
            // given
            long paintingId = 1L, hotelId = 2L;
            PaintingPlacement placement = placement(
                    5L, painting(paintingId, 9L), hotel(hotelId), PlacementStatus.EXHIBITED);
            placement.setNote("old");
            when(paintingPlacementQueryService.getActivePlacement(paintingId)).thenReturn(placement);

            // when
            service.addNote(hotelId, paintingId, new AddNoteRequest("new"));

            // then
            assertThat(placement.getNote()).isEqualTo("new");
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when no active placement exists")
        void throwsWhenNoActivePlacementExists() {
            // given
            long paintingId = 1L, hotelId = 2L;
            when(paintingPlacementQueryService.getActivePlacement(paintingId))
                    .thenThrow(new ResourceNotFoundException(PaintingPlacement.class, "id" + paintingId));

            // when & then
            assertThatThrownBy(() -> service.addNote(hotelId, paintingId, new AddNoteRequest("x")))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ---------------------------------------------------------------------
    // prepareForClientShipping / prepareForClientReception
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("prepareForClient")
    class PrepareForClient {

        @Test
        @DisplayName("transitions to waiting for shipping to client successfully")
        void prepareForClientShipping_transitions() {
            // given
            long paintingId = 1L;
            PaintingPlacement placement = placement(
                    5L, painting(paintingId, 9L), hotel(2L), PlacementStatus.EXHIBITED);
            when(paintingPlacementQueryService.getActivePlacement(paintingId)).thenReturn(placement);

            // when
            service.prepareForClientShipping(paintingId);

            // then
            assertThat(placement.getPlacementStatus())
                    .isEqualTo(PlacementStatus.WAITING_FOR_SHIPPING_TO_CLIENT);
        }

        @Test
        @DisplayName("transitions to waiting for reception by client successfully")
        void prepareForClientReception_transitions() {
            // given
            long paintingId = 1L;
            PaintingPlacement placement = placement(
                    5L, painting(paintingId, 9L), hotel(2L), PlacementStatus.EXHIBITED);
            when(paintingPlacementQueryService.getActivePlacement(paintingId)).thenReturn(placement);

            // when
            service.prepareForClientReception(paintingId);

            // then
            assertThat(placement.getPlacementStatus())
                    .isEqualTo(PlacementStatus.WAITING_FOR_RECEPTION_BY_CLIENT);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when no active placement for shipping preparation")
        void prepareForClientShipping_throwsWhenNoActivePlacement() {
            // given
            long paintingId = 1L;
            when(paintingPlacementQueryService.getActivePlacement(paintingId))
                    .thenThrow(new ResourceNotFoundException(PaintingPlacement.class, "id" + paintingId));

            // when & then
            assertThatThrownBy(() -> service.prepareForClientShipping(paintingId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when no active placement for reception preparation")
        void prepareForClientReception_throwsWhenNoActivePlacement() {
            // given
            long paintingId = 1L;
            when(paintingPlacementQueryService.getActivePlacement(paintingId))
                    .thenThrow(new ResourceNotFoundException(PaintingPlacement.class, "id" + paintingId));

            // when & then
            assertThatThrownBy(() -> service.prepareForClientReception(paintingId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}