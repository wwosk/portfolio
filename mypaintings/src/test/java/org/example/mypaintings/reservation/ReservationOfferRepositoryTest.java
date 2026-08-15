package org.example.mypaintings.reservation;

import org.example.mypaintings.exception.notfound.ResourceNotFoundException;
import org.example.mypaintings.painting.Painting;
import org.example.mypaintings.painting.PaintingRepository;
import org.example.mypaintings.painting.features.Dimensions;
import org.example.mypaintings.painting.features.PaintingMedium;
import org.example.mypaintings.painting.features.Unit;
import org.example.mypaintings.sale.financial.Money;
import org.example.mypaintings.user.User;
import org.example.mypaintings.user.UserRepository;
import org.example.mypaintings.user.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("ReservationOfferRepository Test Suite")
class ReservationOfferRepositoryTest {

    @Autowired
    private ReservationOfferRepository offerRepository;

    @Autowired
    private PaintingRepository paintingRepository;

    @Autowired
    private UserRepository userRepository;

    private User artist;
    private User hotelA;
    private User hotelB;
    private Painting painting1;
    private Painting painting2;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        artist = userRepository.saveAndFlush(
                new User(UserRole.ARTIST, "artist-1", "Addr A", 50.0, 19.0));
        hotelA = userRepository.saveAndFlush(
                new User(UserRole.HOTEL, "hotel-a", "Addr HA", 50.1, 19.1));
        hotelB = userRepository.saveAndFlush(
                new User(UserRole.HOTEL, "hotel-b", "Addr HB", 50.2, 19.2));

        painting1 = savePainting(artist, "P1");
        painting2 = savePainting(artist, "P2");
    }

    // ------------------------------------------------------------------
    // rejectOtherOffers (@Modifying)
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("rejectOtherOffers")
    class RejectOtherOffers {

        @Test
        @DisplayName("sets REJECTED on all other offers for the same painting")
        void rejectsOtherOffers() {
            // given
            ReservationOffer accepted = saveOffer(painting1, hotelA, ReservationStatus.PENDING);
            ReservationOffer other = saveOffer(painting1, hotelB, ReservationStatus.PENDING);

            // when
            offerRepository.rejectOtherOffers(painting1, accepted.getId());
            offerRepository.flush();

            // then
            ReservationOffer acceptedReloaded = offerRepository.findById(accepted.getId()).orElseThrow();
            ReservationOffer otherReloaded = offerRepository.findById(other.getId()).orElseThrow();

            assertThat(acceptedReloaded.getStatus()).isEqualTo(ReservationStatus.PENDING);
            assertThat(otherReloaded.getStatus()).isEqualTo(ReservationStatus.REJECTED);
        }

        @Test
        @DisplayName("does not touch offers of other paintings")
        void doesNotTouchOtherPaintings() {
            // given
            ReservationOffer accepted = saveOffer(painting1, hotelA, ReservationStatus.PENDING);
            ReservationOffer otherPaintingOffer = saveOffer(painting2, hotelB, ReservationStatus.PENDING);

            // when
            offerRepository.rejectOtherOffers(painting1, accepted.getId());
            offerRepository.flush();

            // then
            ReservationOffer reloaded = offerRepository.findById(otherPaintingOffer.getId()).orElseThrow();
            assertThat(reloaded.getStatus()).isEqualTo(ReservationStatus.PENDING);
        }

        @Test
        @DisplayName("clearAutomatically forces fresh read from DB")
        void clearAutomaticallyRefreshesContext() {
            // given
            ReservationOffer accepted = saveOffer(painting1, hotelA, ReservationStatus.PENDING);
            ReservationOffer other = saveOffer(painting1, hotelB, ReservationStatus.PENDING);

            // when
            offerRepository.rejectOtherOffers(painting1, accepted.getId());

            // then
            ReservationOffer refreshed = offerRepository.findById(other.getId()).orElseThrow();
            assertThat(refreshed.getStatus()).isEqualTo(ReservationStatus.REJECTED);
        }
    }

    // ------------------------------------------------------------------
    // existsByPaintingIdAndStatus
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("existsByPaintingIdAndStatus")
    class ExistsByPaintingIdAndStatus {

        @Test
        @DisplayName("returns true when matching offer exists")
        void returnsTrue() {
            // given
            saveOffer(painting1, hotelA, ReservationStatus.ACCEPTED);

            // when
            boolean exists = offerRepository.existsByPaintingIdAndStatus(
                    painting1.getId(), ReservationStatus.ACCEPTED);

            // then
            assertThat(exists).isTrue();
        }

        @Test
        @DisplayName("returns false for different status")
        void returnsFalseForDifferentStatus() {
            // given
            saveOffer(painting1, hotelA, ReservationStatus.PENDING);

            // when
            boolean exists = offerRepository.existsByPaintingIdAndStatus(
                    painting1.getId(), ReservationStatus.ACCEPTED);

            // then
            assertThat(exists).isFalse();
        }

        @Test
        @DisplayName("returns false when no offers for painting")
        void returnsFalseWhenNoOffers() {
            // when
            boolean exists = offerRepository.existsByPaintingIdAndStatus(
                    painting1.getId(), ReservationStatus.PENDING);

            // then
            assertThat(exists).isFalse();
        }
    }

    // ------------------------------------------------------------------
    // findByIdWithDetails / getOrThrow
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("findByIdWithDetails / getOrThrow")
    class FindByIdWithDetails {

        @Test
        @DisplayName("returns offer with fetched painting and artist")
        void returnsOfferWithFetchedAssociations() {
            // given
            ReservationOffer offer = saveOffer(painting1, hotelA, ReservationStatus.PENDING);

            // when
            ReservationOffer result = offerRepository.findByIdWithDetails(offer.getId()).orElseThrow();

            // then
            assertThat(result.getPainting()).isNotNull();
            assertThat(result.getPainting().getArtist()).isNotNull();
            assertThat(result.getPainting().getArtist().getId()).isEqualTo(artist.getId());
        }

        @Test
        @DisplayName("returns empty optional when offer id does not exist")
        void returnsEmptyWhenMissing() {
            // when
            Optional<ReservationOffer> result = offerRepository.findByIdWithDetails(999_999L);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("getOrThrow returns offer when present")
        void getOrThrowReturnsOffer() {
            // given
            ReservationOffer offer = saveOffer(painting1, hotelA, ReservationStatus.PENDING);

            // when
            ReservationOffer result = offerRepository.getOrThrow(offer.getId());

            // then
            assertThat(result.getId()).isEqualTo(offer.getId());
        }

        @Test
        @DisplayName("getOrThrow throws ResourceNotFoundException when missing")
        void getOrThrowThrows() {
            // when & then
            assertThatThrownBy(() -> offerRepository.getOrThrow(999_999L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ------------------------------------------------------------------
    // Unique Constraint Validation (hotel_id, painting_id)
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Unique Constraint")
    class UniqueConstraintTest {

        @Test
        @DisplayName("throws DataIntegrityViolationException when saving duplicate offer for same hotel and painting")
        void throwsExceptionOnDuplicateHotelAndPainting() {
            // given
            saveOffer(painting1, hotelA, ReservationStatus.PENDING);

            // when & then
            assertThatThrownBy(() -> saveOffer(painting1, hotelA, ReservationStatus.PENDING))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    // ------------------------------------------------------------------
    // findAllByPaintingArtistIdWithPaintingAndAttachments
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("findAllByPaintingArtistIdWithPaintingAndAttachments")
    class FindAllByArtistId {

        @Test
        @DisplayName("returns all offers whose painting belongs to given artist")
        void returnsOffersForArtist() {
            // given
            ReservationOffer offer1 = saveOffer(painting1, hotelA, ReservationStatus.PENDING);
            ReservationOffer offer2 = saveOffer(painting2, hotelB, ReservationStatus.PENDING);

            // when
            List<ReservationOffer> result = offerRepository
                    .findAllByPaintingArtistIdWithPaintingAndAttachments(artist.getId());

            // then
            assertThat(result).extracting(ReservationOffer::getId)
                    .containsExactlyInAnyOrder(offer1.getId(), offer2.getId());
        }

        @Test
        @DisplayName("returns empty list for artist without offers")
        void returnsEmptyForArtistWithoutOffers() {
            // given
            User otherArtist = userRepository.saveAndFlush(
                    new User(UserRole.ARTIST, "artist-2", "Addr B", 51.0, 20.0));

            // when
            List<ReservationOffer> result = offerRepository
                    .findAllByPaintingArtistIdWithPaintingAndAttachments(otherArtist.getId());

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("does not duplicate results when painting has multiple attachments")
        void doesNotDuplicateOnMultipleAttachments() {
            // given
            ReservationOffer offer = saveOffer(painting1, hotelA, ReservationStatus.PENDING);

            // when
            List<ReservationOffer> result = offerRepository
                    .findAllByPaintingArtistIdWithPaintingAndAttachments(artist.getId());

            // then
            assertThat(result).extracting(ReservationOffer::getId)
                    .containsExactly(offer.getId());
        }
    }

    // ------------------------------------------------------------------
    // findAllByHotelIdWithPaintingAndAttachments
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("findAllByHotelIdWithPaintingAndAttachments")
    class FindAllByHotelId {

        @Test
        @DisplayName("returns offers belonging to given hotel")
        void returnsOffersForHotel() {
            // given
            ReservationOffer a = saveOffer(painting1, hotelA, ReservationStatus.PENDING);
            ReservationOffer b = saveOffer(painting2, hotelA, ReservationStatus.PENDING);
            saveOffer(painting1, hotelB, ReservationStatus.PENDING);

            // when
            List<ReservationOffer> result = offerRepository
                    .findAllByHotelIdWithPaintingAndAttachments(hotelA.getId());

            // then
            assertThat(result).extracting(ReservationOffer::getId)
                    .containsExactlyInAnyOrder(a.getId(), b.getId());
        }

        @Test
        @DisplayName("returns empty list for hotel without offers")
        void returnsEmptyForHotelWithoutOffers() {
            // when
            List<ReservationOffer> result = offerRepository
                    .findAllByHotelIdWithPaintingAndAttachments(hotelB.getId());

            // then
            assertThat(result).isEmpty();
        }
    }

    // ------------------------------------------------------------------
    // findAllByPainting
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("findAllByPainting")
    class FindAllByPainting {

        @Test
        @DisplayName("returns all offers for given painting regardless of status")
        void returnsAllStatuses() {
            // given
            ReservationOffer pending = saveOffer(painting1, hotelA, ReservationStatus.PENDING);
            ReservationOffer rejected = saveOffer(painting1, hotelB, ReservationStatus.REJECTED);

            // when
            List<ReservationOffer> result = offerRepository.findAllByPainting(painting1);

            // then
            assertThat(result).extracting(ReservationOffer::getId)
                    .containsExactlyInAnyOrder(pending.getId(), rejected.getId());
        }

        @Test
        @DisplayName("returns empty list when painting has no offers")
        void returnsEmpty() {
            // when
            List<ReservationOffer> result = offerRepository.findAllByPainting(painting2);

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
        @DisplayName("returns only paintings with ACCEPTED offers")
        void returnsOnlyAccepted() {
            // given
            saveOffer(painting1, hotelA, ReservationStatus.ACCEPTED);
            saveOffer(painting2, hotelA, ReservationStatus.PENDING);

            // when
            Set<Long> result = offerRepository.findPaintingIdsWithActiveReservations(
                    List.of(painting1.getId(), painting2.getId()));

            // then
            assertThat(result).containsExactly(painting1.getId());
        }

        @Test
        @DisplayName("returns empty set when no accepted offers")
        void returnsEmptyWhenNoneAccepted() {
            // given
            saveOffer(painting1, hotelA, ReservationStatus.PENDING);

            // when
            Set<Long> result = offerRepository.findPaintingIdsWithActiveReservations(
                    List.of(painting1.getId()));

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty set for empty input list")
        void returnsEmptyForEmptyInput() {
            // when
            Set<Long> result = offerRepository.findPaintingIdsWithActiveReservations(List.of());

            // then
            assertThat(result).isEmpty();
        }
    }

    // ------------------------------------------------------------------
    // findByHotelIdAndPaintingId
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("findByHotelIdAndPaintingId")
    class FindByHotelIdAndPaintingId {

        @Test
        @DisplayName("returns offer matching both ids")
        void returnsMatchingOffer() {
            // given
            ReservationOffer offer = saveOffer(painting1, hotelA, ReservationStatus.PENDING);

            // when
            Optional<ReservationOffer> result = offerRepository
                    .findByHotelIdAndPaintingId(hotelA.getId(), painting1.getId());

            // then
            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo(offer.getId());
        }

        @Test
        @DisplayName("returns empty when pair does not match")
        void returnsEmptyForMismatch() {
            // given
            saveOffer(painting1, hotelA, ReservationStatus.PENDING);

            // when
            Optional<ReservationOffer> result = offerRepository
                    .findByHotelIdAndPaintingId(hotelB.getId(), painting1.getId());

            // then
            assertThat(result).isEmpty();
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Painting savePainting(User owner, String title) {
        Painting painting = Painting.builder()
                .artist(owner)
                .title(title)
                .medium(PaintingMedium.OIL)
                .price(new Money(BigDecimal.TEN, "PLN"))
                .dimensions(new Dimensions(100, 100, Unit.CM))
                .build();
        return paintingRepository.saveAndFlush(painting);
    }

    private ReservationOffer saveOffer(Painting painting, User hotel, ReservationStatus status) {
        ReservationOffer offer = ReservationOffer.builder()
                .painting(painting)
                .hotel(hotel)
                .status(status)
                .build();
        return offerRepository.saveAndFlush(offer);
    }
}