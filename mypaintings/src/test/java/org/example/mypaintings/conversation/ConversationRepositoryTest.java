package org.example.mypaintings.conversation;

import org.example.mypaintings.exception.notfound.ResourceNotFoundException;
import org.example.mypaintings.painting.Painting;
import org.example.mypaintings.painting.features.Dimensions;
import org.example.mypaintings.painting.features.PaintingMedium;
import org.example.mypaintings.painting.features.Unit;
import org.example.mypaintings.reservation.ReservationOffer;
import org.example.mypaintings.reservation.ReservationStatus;
import org.example.mypaintings.sale.financial.Money;
import org.example.mypaintings.user.User;
import org.example.mypaintings.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@DisplayName("ConversationRepository Test Suite")
class ConversationRepositoryTest {

    @Autowired
    private ConversationRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    private User hotel;
    private User artist;
    private Painting painting;

    @BeforeEach
    void setUp() {
        hotel = persistUser(UserRole.HOTEL, "hotel1");
        artist = persistUser(UserRole.ARTIST, "artist1");
        painting = persistPainting(artist, "Sunset");
        entityManager.flush();
        entityManager.clear();
    }

    @Nested
    @DisplayName("findActiveByHotelIdWithDetails")
    class FindActiveByHotelIdWithDetails {

        @Test
        @DisplayName("fetches painting, artist, and hotel details successfully")
        void findActiveByHotelIdWithDetails_fetchesPaintingArtistAndHotel() {
            // given
            Conversation conv = persistConversation(painting, hotel, null);
            entityManager.flush();
            entityManager.clear();

            // when
            List<Conversation> result = repository.findActiveByHotelIdWithDetails(hotel.getId());

            // then
            assertThat(result).hasSize(1);
            Conversation found = result.getFirst();
            assertThat(found.getPainting().getTitle()).isEqualTo("Sunset");
            assertThat(found.getPainting().getArtist().getNickname()).isEqualTo("artist1");
            assertThat(found.getHotel().getNickname()).isEqualTo("hotel1");
            assertThat(found.getId()).isEqualTo(conv.getId());
        }

        @Test
        @DisplayName("excludes conversations archived by hotel or both")
        void findActiveByHotelIdWithDetails_excludesArchivedByHotelAndBoth() {
            // given
            persistConversation(painting, hotel, null);
            persistConversation(persistPainting(artist, "P2"), hotel, ArchivedBy.HOTEL);
            persistConversation(persistPainting(artist, "P3"), hotel, ArchivedBy.BOTH);
            entityManager.flush();
            entityManager.clear();

            // when
            List<Conversation> result = repository.findActiveByHotelIdWithDetails(hotel.getId());

            // then
            assertThat(result).hasSize(1);
            assertThat(result.getFirst().getArchived()).isNull();
        }

        @Test
        @DisplayName("includes conversations archived by artist")
        void findActiveByHotelIdWithDetails_includesArchivedByArtist() {
            // given
            persistConversation(painting, hotel, ArchivedBy.ARTIST);
            entityManager.flush();
            entityManager.clear();

            // when
            List<Conversation> result = repository.findActiveByHotelIdWithDetails(hotel.getId());

            // then
            assertThat(result).hasSize(1);
            assertThat(result.getFirst().getArchived()).isEqualTo(ArchivedBy.ARTIST);
        }

        @Test
        @DisplayName("returns empty list when hotel has no conversations")
        void findActiveByHotelIdWithDetails_returnsEmpty_whenHotelHasNoConversations() {
            // given
            User otherHotel = persistUser(UserRole.HOTEL, "other-hotel");
            entityManager.flush();
            entityManager.clear();

            // when
            List<Conversation> result = repository.findActiveByHotelIdWithDetails(otherHotel.getId());

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findActiveByPaintingArtistIdWithDetails")
    class FindActiveByPaintingArtistIdWithDetails {

        @Test
        @DisplayName("fetches details successfully for artist")
        void findActiveByPaintingArtistIdWithDetails_fetchesDetails() {
            // given
            Conversation conv = persistConversation(painting, hotel, null);
            entityManager.flush();
            entityManager.clear();

            // when
            List<Conversation> result = repository.findActiveByPaintingArtistIdWithDetails(artist.getId());

            // then
            assertThat(result).hasSize(1);
            Conversation found = result.getFirst();
            assertThat(found.getPainting().getArtist().getNickname()).isEqualTo("artist1");
            assertThat(found.getHotel().getNickname()).isEqualTo("hotel1");
            assertThat(found.getId()).isEqualTo(conv.getId());
        }

        @Test
        @DisplayName("excludes conversations archived by artist or both")
        void findActiveByPaintingArtistIdWithDetails_excludesArchivedByArtistAndBoth() {
            // given
            persistConversation(painting, hotel, null);
            persistConversation(persistPainting(artist, "P2"), hotel, ArchivedBy.ARTIST);
            persistConversation(persistPainting(artist, "P3"), hotel, ArchivedBy.BOTH);
            entityManager.flush();
            entityManager.clear();

            // when
            List<Conversation> result = repository.findActiveByPaintingArtistIdWithDetails(artist.getId());

            // then
            assertThat(result).hasSize(1);
            assertThat(result.getFirst().getArchived()).isNull();
        }

        @Test
        @DisplayName("includes conversations archived by hotel")
        void findActiveByPaintingArtistIdWithDetails_includesArchivedByHotel() {
            // given
            persistConversation(painting, hotel, ArchivedBy.HOTEL);
            entityManager.flush();
            entityManager.clear();

            // when
            List<Conversation> result = repository.findActiveByPaintingArtistIdWithDetails(artist.getId());

            // then
            assertThat(result).hasSize(1);
            assertThat(result.getFirst().getArchived()).isEqualTo(ArchivedBy.HOTEL);
        }
    }

    @Nested
    @DisplayName("findByPaintingAndHotel")
    class FindByPaintingAndHotel {

        @Test
        @DisplayName("returns conversation when painting and hotel match")
        void returnsConversationWhenMatching() {
            // given
            Conversation conv = persistConversation(painting, hotel, null);
            entityManager.flush();
            entityManager.clear();

            // when
            Optional<Conversation> result = repository.findByPaintingAndHotel(painting, hotel);

            // then
            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo(conv.getId());
        }

        @Test
        @DisplayName("returns empty optional when pair does not match")
        void returnsEmptyWhenNoMatch() {
            // given
            User otherHotel = persistUser(UserRole.HOTEL, "other-hotel");
            persistConversation(painting, hotel, null);
            entityManager.flush();
            entityManager.clear();

            // when
            Optional<Conversation> result = repository.findByPaintingAndHotel(painting, otherHotel);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findAllByPainting")
    class FindAllByPainting {

        @Test
        @DisplayName("returns all conversations for given painting")
        void returnsAllForPainting() {
            // given
            User hotel2 = persistUser(UserRole.HOTEL, "hotel2");
            Conversation c1 = persistConversation(painting, hotel, null);
            Conversation c2 = persistConversation(painting, hotel2, null);
            entityManager.flush();
            entityManager.clear();

            // when
            List<Conversation> result = repository.findAllByPainting(painting);

            // then
            assertThat(result).extracting(Conversation::getId)
                    .containsExactlyInAnyOrder(c1.getId(), c2.getId());
        }

        @Test
        @DisplayName("returns empty list when painting has no conversations")
        void returnsEmptyWhenNone() {
            // given
            Painting otherPainting = persistPainting(artist, "Other");
            entityManager.flush();
            entityManager.clear();

            // when
            List<Conversation> result = repository.findAllByPainting(otherPainting);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getOrThrow")
    class GetOrThrow {

        @Test
        @DisplayName("returns conversation when exists")
        void getOrThrow_returnsConversation_whenExists() {
            // given
            Conversation conv = persistConversation(painting, hotel, null);
            entityManager.flush();
            entityManager.clear();

            // when
            Conversation found = repository.getOrThrow(conv.getId());

            // then
            assertThat(found.getId()).isEqualTo(conv.getId());
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when missing")
        void getOrThrow_throws_whenMissing() {
            // when & then
            assertThatThrownBy(() -> repository.getOrThrow(999L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("Unique Constraint")
    class UniqueConstraintTest {

        @Test
        @DisplayName("throws exception when duplicate conversation for same painting and hotel")
        void throwsExceptionOnDuplicate() {
            // given
            persistConversation(painting, hotel, null);
            entityManager.flush();

            // when & then
            assertThatThrownBy(() -> {
                persistConversation(painting, hotel, null);
                entityManager.flush();
            }).isInstanceOf(RuntimeException.class);
        }
    }

    // --- helpers ---

    private User persistUser(UserRole role, String nickname) {
        return entityManager.persist(new User(role, nickname, "addr", 0.0, 0.0));
    }

    private Painting persistPainting(User artist, String title) {
        Painting p = Painting.builder()
                .artist(artist)
                .title(title)
                .dimensions(new Dimensions(10.0, 10.0, Unit.CM))
                .price(new Money(BigDecimal.TEN, "PLN"))
                .medium(PaintingMedium.OIL)
                .build();
        return entityManager.persist(p);
    }

    private ReservationOffer persistOffer(Painting painting, User hotel) {
        ReservationOffer offer = ReservationOffer.builder()
                .painting(painting)
                .hotel(hotel)
                .status(ReservationStatus.PENDING)
                .build();
        return entityManager.persist(offer);
    }

    private Conversation persistConversation(Painting painting, User hotel, ArchivedBy archived) {
        ReservationOffer offer = persistOffer(painting, hotel);
        Conversation c = new Conversation(painting, hotel, offer, archived, null);
        return entityManager.persist(c);
    }
}