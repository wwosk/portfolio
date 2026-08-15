package org.example.mypaintings.conversation;

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
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@DisplayName("MessageRepository Test Suite")
class MessageRepositoryTest {

    @Autowired
    private MessageRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    private User hotel;
    private User artist;
    private Painting painting;
    private Conversation conversation;

    @BeforeEach
    void setUp() {
        hotel = persistUser(UserRole.HOTEL, "hotel1");
        artist = persistUser(UserRole.ARTIST, "artist1");
        painting = persistPainting(artist, "Sunset");
        conversation = persistConversation(painting, hotel);
        entityManager.flush();
        entityManager.clear();
    }

    // ------------------------------------------------------------------
    // findByConversationIdOrderByCreatedAtAsc
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("findByConversationIdOrderByCreatedAtAsc")
    class FindByConversationIdSorted {

        @Test
        @DisplayName("returns messages sorted by creation date ascending")
        void returnsMessagesSortedByCreatedAt() {
            // given
            Instant t1 = Instant.parse("2026-01-01T10:00:00Z");
            Instant t2 = Instant.parse("2026-01-01T11:00:00Z");
            Instant t3 = Instant.parse("2026-01-01T12:00:00Z");

            persistMessage(conversation, artist, hotel, "third", t3);
            persistMessage(conversation, hotel, artist, "first", t1);
            persistMessage(conversation, artist, hotel, "second", t2);
            entityManager.flush();
            entityManager.clear();

            // when
            List<Message> result = repository.findByConversationIdOrderByCreatedAtAsc(conversation.getId());

            // then
            assertThat(result).hasSize(3);
            assertThat(result)
                    .extracting(Message::getMessageContent)
                    .containsExactly("first", "second", "third");
            assertThat(result)
                    .extracting(Message::getCreatedAt)
                    .containsExactly(t1, t2, t3);
        }

        @Test
        @DisplayName("returns only messages from the specified conversation")
        void returnsOnlyMessagesFromGivenConversation() {
            // given
            Painting otherPainting = persistPainting(artist, "Other");
            Conversation otherConversation = persistConversation(otherPainting, hotel);

            persistMessage(conversation, artist, hotel, "mine-1", Instant.parse("2026-01-01T10:00:00Z"));
            persistMessage(conversation, hotel, artist, "mine-2", Instant.parse("2026-01-01T11:00:00Z"));
            persistMessage(otherConversation, artist, hotel, "other-1", Instant.parse("2026-01-01T12:00:00Z"));
            entityManager.flush();
            entityManager.clear();

            // when
            List<Message> result = repository.findByConversationIdOrderByCreatedAtAsc(conversation.getId());

            // then
            assertThat(result)
                    .extracting(Message::getMessageContent)
                    .containsExactly("mine-1", "mine-2");
        }

        @Test
        @DisplayName("returns empty list when conversation id does not exist")
        void returnsEmptyListWhenConversationMissing() {
            // when
            List<Message> result = repository.findByConversationIdOrderByCreatedAtAsc(999_999L);

            // then
            assertThat(result).isEmpty();
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

    private Conversation persistConversation(Painting painting, User hotel) {
        ReservationOffer offer = ReservationOffer.builder()
                .painting(painting)
                .hotel(hotel)
                .status(ReservationStatus.PENDING)
                .build();
        entityManager.persist(offer);

        Conversation c = new Conversation(painting, hotel, offer, null, null);
        return entityManager.persist(c);
    }

    private void persistMessage(Conversation conversation, User sender, User receiver,
                                String content, Instant createdAt) {
        Message msg = Message.builder()
                .conversation(conversation)
                .sender(sender)
                .receiver(receiver)
                .messageContent(content)
                .createdAt(createdAt)
                .build();
        entityManager.persist(msg);
    }
}
