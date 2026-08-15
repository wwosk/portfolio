package org.example.mypaintings.reservation;

import org.example.mypaintings.exception.notfound.ResourceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReservationQueryService Test Suite")
class ReservationQueryServiceTest {

    @Mock
    private ReservationOfferRepository reservationOfferRepository;

    @InjectMocks
    private ReservationQueryService reservationQueryService;

    private static final long PAINTING_ID = 42L;
    private static final long OFFER_ID = 7L;
    private static final long ARTIST_ID = 11L;
    private static final long HOTEL_ID = 99L;

    // ------------------------------------------------------------------
    // hasActiveReservation
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("hasActiveReservation")
    class HasActiveReservation {

        @Test
        @DisplayName("returns true when repository reports an ACCEPTED offer")
        void returnsTrueWhenAcceptedOfferExists() {
            // given
            when(reservationOfferRepository.existsByPaintingIdAndStatus(
                    PAINTING_ID, ReservationStatus.ACCEPTED))
                    .thenReturn(true);

            // when
            boolean result = reservationQueryService.hasActiveReservation(PAINTING_ID);

            // then
            assertThat(result).isTrue();
            verify(reservationOfferRepository)
                    .existsByPaintingIdAndStatus(PAINTING_ID, ReservationStatus.ACCEPTED);
        }

        @Test
        @DisplayName("returns false when repository reports no ACCEPTED offer")
        void returnsFalseWhenNoAcceptedOffer() {
            // given
            when(reservationOfferRepository.existsByPaintingIdAndStatus(
                    PAINTING_ID, ReservationStatus.ACCEPTED))
                    .thenReturn(false);

            // when
            boolean result = reservationQueryService.hasActiveReservation(PAINTING_ID);

            // then
            assertThat(result).isFalse();
            verify(reservationOfferRepository)
                    .existsByPaintingIdAndStatus(PAINTING_ID, ReservationStatus.ACCEPTED);
        }

        @Test
        @DisplayName("queries specifically for ACCEPTED status")
        void queriesSpecificallyForAcceptedStatus() {
            // given
            when(reservationOfferRepository.existsByPaintingIdAndStatus(
                    PAINTING_ID, ReservationStatus.ACCEPTED))
                    .thenReturn(false);

            // when
            reservationQueryService.hasActiveReservation(PAINTING_ID);

            // then
            verify(reservationOfferRepository)
                    .existsByPaintingIdAndStatus(PAINTING_ID, ReservationStatus.ACCEPTED);
        }
    }

    // ------------------------------------------------------------------
    // getValidOrThrow
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("getValidOrThrow")
    class GetValidOrThrow {

        @Test
        @DisplayName("returns offer when repository finds it")
        void returnsOfferWhenFound() {
            // given
            ReservationOffer offer = mock(ReservationOffer.class);
            when(reservationOfferRepository.findById(OFFER_ID))
                    .thenReturn(Optional.of(offer));

            // when
            ReservationOffer result = reservationQueryService.getValidOrThrow(OFFER_ID);

            // then
            assertThat(result).isSameAs(offer);
            verify(reservationOfferRepository).findById(OFFER_ID);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when offer is missing")
        void throwsWhenMissing() {
            // given
            when(reservationOfferRepository.findById(OFFER_ID))
                    .thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> reservationQueryService.getValidOrThrow(OFFER_ID))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(reservationOfferRepository).findById(OFFER_ID);
        }

        @Test
        @DisplayName("uses plain findById, not findByIdWithDetails")
        void usesPlainFindById() {
            // given
            ReservationOffer offer = mock(ReservationOffer.class);
            when(reservationOfferRepository.findById(OFFER_ID))
                    .thenReturn(Optional.of(offer));

            // when
            reservationQueryService.getValidOrThrow(OFFER_ID);

            // then
            verify(reservationOfferRepository).findById(OFFER_ID);
            verify(reservationOfferRepository, never())
                    .findByIdWithDetails(OFFER_ID);
        }
    }

    // ------------------------------------------------------------------
    // getOffersByArtistId
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("getOffersByArtistId")
    class GetOffersByArtistId {

        @Test
        @DisplayName("delegates to repository and returns result")
        void delegatesToRepository() {
            // given
            List<ReservationOffer> expected = List.of(
                    mock(ReservationOffer.class), mock(ReservationOffer.class));

            when(reservationOfferRepository
                    .findAllByPaintingArtistIdWithPaintingAndAttachments(ARTIST_ID))
                    .thenReturn(expected);

            // when
            List<ReservationOffer> result = reservationQueryService.getOffersByArtistId(ARTIST_ID);

            // then
            assertThat(result).containsExactlyElementsOf(expected);
            verify(reservationOfferRepository)
                    .findAllByPaintingArtistIdWithPaintingAndAttachments(ARTIST_ID);
        }

        @Test
        @DisplayName("returns empty list when artist has no offers")
        void returnsEmptyList() {
            // given
            when(reservationOfferRepository
                    .findAllByPaintingArtistIdWithPaintingAndAttachments(ARTIST_ID))
                    .thenReturn(Collections.emptyList());

            // when
            List<ReservationOffer> result = reservationQueryService.getOffersByArtistId(ARTIST_ID);

            // then
            assertThat(result).isEmpty();
        }
    }

    // ------------------------------------------------------------------
    // getOffersByHotelId
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("getOffersByHotelId")
    class GetOffersByHotelId {

        @Test
        @DisplayName("delegates to repository and returns result")
        void delegatesToRepository() {
            // given
            List<ReservationOffer> expected = List.of(mock(ReservationOffer.class));

            when(reservationOfferRepository
                    .findAllByHotelIdWithPaintingAndAttachments(HOTEL_ID))
                    .thenReturn(expected);

            // when
            List<ReservationOffer> result = reservationQueryService.getOffersByHotelId(HOTEL_ID);

            // then
            assertThat(result).containsExactlyElementsOf(expected);
            verify(reservationOfferRepository)
                    .findAllByHotelIdWithPaintingAndAttachments(HOTEL_ID);
        }

        @Test
        @DisplayName("returns empty list when hotel has no offers")
        void returnsEmptyList() {
            // given
            when(reservationOfferRepository
                    .findAllByHotelIdWithPaintingAndAttachments(HOTEL_ID))
                    .thenReturn(Collections.emptyList());

            // when
            List<ReservationOffer> result = reservationQueryService.getOffersByHotelId(HOTEL_ID);

            // then
            assertThat(result).isEmpty();
        }
    }

    // ------------------------------------------------------------------
    // findPaintingIdsWithActiveReservations
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("findPaintingIdsWithActiveReservations")
    class FindPaintingIdsWithActiveReservations {

        @Test
        @DisplayName("returns empty set and skips repository when input is null")
        void returnsEmptySetWhenInputIsNull() {
            // when
            Set<Long> result = reservationQueryService.findPaintingIdsWithActiveReservations(null);

            // then
            assertThat(result).isEmpty();
            verifyNoInteractions(reservationOfferRepository);
        }

        @Test
        @DisplayName("returns empty set and skips repository when input is empty")
        void returnsEmptySetWhenInputIsEmpty() {
            // when
            Set<Long> result = reservationQueryService
                    .findPaintingIdsWithActiveReservations(Collections.emptyList());

            // then
            assertThat(result).isEmpty();
            verifyNoInteractions(reservationOfferRepository);
        }

        @Test
        @DisplayName("delegates to repository when input is non-empty")
        void delegatesWhenInputNonEmpty() {
            // given
            List<Long> ids = List.of(1L, 2L, 3L);
            Set<Long> expected = Set.of(1L, 3L);

            when(reservationOfferRepository.findPaintingIdsWithActiveReservations(ids))
                    .thenReturn(expected);

            // when
            Set<Long> result = reservationQueryService
                    .findPaintingIdsWithActiveReservations(ids);

            // then
            assertThat(result).isEqualTo(expected);
            verify(reservationOfferRepository).findPaintingIdsWithActiveReservations(ids);
        }

        @Test
        @DisplayName("returns empty set when repository returns empty set")
        void returnsEmptySetWhenRepositoryReturnsEmpty() {
            // given
            List<Long> ids = List.of(1L, 2L);
            when(reservationOfferRepository.findPaintingIdsWithActiveReservations(ids))
                    .thenReturn(Set.of());

            // when
            Set<Long> result = reservationQueryService
                    .findPaintingIdsWithActiveReservations(ids);

            // then
            assertThat(result).isEmpty();
        }
    }
}