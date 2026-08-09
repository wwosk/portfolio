package org.example.mypaintings.conversation;

import org.example.mypaintings.exception.access.UnauthorizedAccessException;
import org.example.mypaintings.exception.messages.SelfMessageException;
import org.example.mypaintings.painting.Painting;
import org.example.mypaintings.painting.features.Dimensions;
import org.example.mypaintings.painting.features.PaintingMedium;
import org.example.mypaintings.painting.features.Unit;
import org.example.mypaintings.reservation.ReservationOffer;
import org.example.mypaintings.sale.financial.Money;
import org.example.mypaintings.user.User;
import org.example.mypaintings.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConversationTest {

    private User hotel;
    private User artist;
    private User stranger;
    private Painting painting;
    private ReservationOffer offer;
    private Conversation conversation;

    @BeforeEach
    void setUp() {
        // given
        hotel = new User(UserRole.HOTEL, "GrandHotel", "Paris", 48.8566, 2.3522);
        artist = new User(UserRole.ARTIST, "vanGogh", "Amsterdam", 52.3676, 4.9041);
        stranger = new User(UserRole.ARTIST, "stranger", "Berlin", 52.52, 13.405);

        setId(hotel, 1L);
        setId(artist, 2L);
        setId(stranger, 3L);

        painting = Painting.builder()
                .artist(artist)
                .title("Starry Night")
                .dimensions(new Dimensions(73.7, 92.1, Unit.CM))
                .description("A masterpiece")
                .price(new Money(new BigDecimal("1000.00"), "EUR"))
                .medium(PaintingMedium.OIL)
                .build();

        offer = ReservationOffer.builder()
                .painting(painting)
                .hotel(hotel)
                .build();

        conversation = Conversation.builder()
                .painting(painting)
                .hotel(hotel)
                .offer(offer)
                .build();
    }

    private static void setId(Object target, Long id) {
        try {
            var field = target.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(target, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to set ID via reflection", e);
        }
    }

    @Nested
    @DisplayName("Builder and Constructor")
    class BuilderTests {

        @Test
        @DisplayName("Should create conversation with valid data")
        void shouldCreateConversation_whenAllDataValid() {
            // then
            assertThat(conversation.getPainting()).isSameAs(painting);
            assertThat(conversation.getHotel()).isSameAs(hotel);
            assertThat(conversation.getOffer()).isSameAs(offer);
            assertThat(conversation.getArchived()).isNull();
        }

        @Test
        @DisplayName("Should initialize empty messages list when provided list is null")
        void shouldInitializeEmptyMessages_whenNull() {
            // when
            Conversation c = Conversation.builder()
                    .painting(painting)
                    .hotel(hotel)
                    .offer(offer)
                    .messages(null)
                    .build();

            // then
            assertThat(c.getMessages()).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("Should keep provided messages list")
        void shouldKeepProvidedMessages() {
            // given
            Message msg = Message.builder()
                    .sender(hotel)
                    .receiver(artist)
                    .messageContent("hi")
                    .conversation(conversation)
                    .build();

            // when
            Conversation c = Conversation.builder()
                    .painting(painting)
                    .hotel(hotel)
                    .offer(offer)
                    .messages(new ArrayList<>(List.of(msg)))
                    .build();

            // then
            assertThat(c.getMessages()).containsExactly(msg);
        }
    }

    @Nested
    @DisplayName("isParticipant")
    class IsParticipantTests {

        @Test
        @DisplayName("Should return true for hotel participant")
        void shouldReturnTrue_forHotel() {
            // when & then
            assertThat(conversation.isParticipant(hotel.getId())).isTrue();
        }

        @Test
        @DisplayName("Should return true for artist participant")
        void shouldReturnTrue_forArtist() {
            // when & then
            assertThat(conversation.isParticipant(artist.getId())).isTrue();
        }

        @Test
        @DisplayName("Should return false for non-participant user")
        void shouldReturnFalse_forStranger() {
            // when & then
            assertThat(conversation.isParticipant(stranger.getId())).isFalse();
        }
    }

    @Nested
    @DisplayName("validateParticipantOrThrow")
    class ValidateParticipantOrThrowTests {

        @Test
        @DisplayName("Should not throw exception for hotel participant")
        void shouldNotThrow_forHotel() {
            // when & then
            conversation.validateParticipantOrThrow(hotel.getId());
        }

        @Test
        @DisplayName("Should not throw exception for artist participant")
        void shouldNotThrow_forArtist() {
            // when & then
            conversation.validateParticipantOrThrow(artist.getId());
        }

        @Test
        @DisplayName("Should throw AccessDeniedException for non-participant user")
        void shouldThrow_forStranger() {
            // when & then
            assertThatThrownBy(() -> conversation.validateParticipantOrThrow(stranger.getId()))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessage("User is not a participant of this conversation");
        }
    }

    @Nested
    @DisplayName("createMessage")
    class CreateMessageTests {

        @Test
        @DisplayName("Should create message from hotel to artist with valid fields")
        void shouldCreateMessage_fromHotelToArtist() {
            // when
            Message msg = conversation.createMessage(hotel, artist, "Hello");

            // then
            assertThat(msg.getSender()).isSameAs(hotel);
            assertThat(msg.getReceiver()).isSameAs(artist);
            assertThat(msg.getMessageContent()).isEqualTo("Hello");
            assertThat(msg.getConversation()).isSameAs(conversation);
            assertThat(msg.getCreatedAt()).isNotNull();
        }

        @Test
        @DisplayName("Should create message from artist to hotel with valid fields")
        void shouldCreateMessage_fromArtistToHotel() {
            // when
            Message msg = conversation.createMessage(artist, hotel, "Hi");

            // then
            assertThat(msg.getSender()).isSameAs(artist);
            assertThat(msg.getReceiver()).isSameAs(hotel);
            assertThat(msg.getConversation()).isSameAs(conversation);
        }

        @Test
        @DisplayName("Should append created message to internal messages list")
        void shouldAddMessageToList() {
            // when
            Message msg = conversation.createMessage(hotel, artist, "Hello");

            // then
            assertThat(conversation.getMessages()).containsExactly(msg);
        }

        @Test
        @DisplayName("Should append multiple messages in sequence order")
        void shouldAppendMessagesInOrder() {
            // when
            Message first = conversation.createMessage(hotel, artist, "1");
            Message second = conversation.createMessage(artist, hotel, "2");
            Message third = conversation.createMessage(hotel, artist, "3");

            // then
            assertThat(conversation.getMessages()).containsExactly(first, second, third);
        }

        @Test
        @DisplayName("Should throw UnauthorizedAccessException when sender is non-participant")
        void shouldThrow_whenSenderIsStranger() {
            // when & then
            assertThatThrownBy(() -> conversation.createMessage(stranger, artist, "hi"))
                    .isInstanceOf(UnauthorizedAccessException.class)
                    .hasMessage("User is not a participant");
        }

        @Test
        @DisplayName("Should throw UnauthorizedAccessException when receiver is non-participant")
        void shouldThrow_whenReceiverIsStranger() {
            // when & then
            assertThatThrownBy(() -> conversation.createMessage(hotel, stranger, "hi"))
                    .isInstanceOf(UnauthorizedAccessException.class)
                    .hasMessage("User is not a participant");
        }

        @Test
        @DisplayName("Should throw SelfMessageException when sender and receiver are the same hotel user")
        void shouldThrow_whenSenderEqualsReceiver() {
            // when & then
            assertThatThrownBy(() -> conversation.createMessage(hotel, hotel, "hi"))
                    .isInstanceOf(SelfMessageException.class)
                    .hasMessage("Cannot send message to self");
        }

        @Test
        @DisplayName("Should throw SelfMessageException when sender and receiver are the same artist user")
        void shouldThrow_whenArtistSendsToSelf() {
            // when & then
            assertThatThrownBy(() -> conversation.createMessage(artist, artist, "hi"))
                    .isInstanceOf(SelfMessageException.class)
                    .hasMessage("Cannot send message to self");
        }

        @Test
        @DisplayName("Should not modify messages list when validation fails during message creation")
        void shouldNotAddMessage_whenValidationFails() {
            // when
            assertThatThrownBy(() -> conversation.createMessage(stranger, artist, "hi"))
                    .isInstanceOf(UnauthorizedAccessException.class);

            // then
            assertThat(conversation.getMessages()).isEmpty();
        }
    }

    @Nested
    @DisplayName("archiveFor")
    class ArchiveForTests {

        @Test
        @DisplayName("Should set status to HOTEL when archived by hotel")
        void shouldSetHotel_whenHotelArchives() {
            // when
            conversation.archiveFor(hotel.getId());

            // then
            assertThat(conversation.getArchived()).isEqualTo(ArchivedBy.HOTEL);
        }

        @Test
        @DisplayName("Should set status to ARTIST when archived by artist")
        void shouldSetArtist_whenArtistArchives() {
            // when
            conversation.archiveFor(artist.getId());

            // then
            assertThat(conversation.getArchived()).isEqualTo(ArchivedBy.ARTIST);
        }

        @Test
        @DisplayName("Should set status to BOTH when archived by hotel then artist")
        void shouldSetBoth_whenHotelThenArtist() {
            // when
            conversation.archiveFor(hotel.getId());
            conversation.archiveFor(artist.getId());

            // then
            assertThat(conversation.getArchived()).isEqualTo(ArchivedBy.BOTH);
        }

        @Test
        @DisplayName("Should set status to BOTH when archived by artist then hotel")
        void shouldSetBoth_whenArtistThenHotel() {
            // when
            conversation.archiveFor(artist.getId());
            conversation.archiveFor(hotel.getId());

            // then
            assertThat(conversation.getArchived()).isEqualTo(ArchivedBy.BOTH);
        }

        @Test
        @DisplayName("Should remain HOTEL when archived by hotel multiple times (idempotent)")
        void shouldBeIdempotent_forHotelTwice() {
            // when
            conversation.archiveFor(hotel.getId());
            conversation.archiveFor(hotel.getId());

            // then
            assertThat(conversation.getArchived()).isEqualTo(ArchivedBy.HOTEL);
        }

        @Test
        @DisplayName("Should remain ARTIST when archived by artist multiple times (idempotent)")
        void shouldBeIdempotent_forArtistTwice() {
            // when
            conversation.archiveFor(artist.getId());
            conversation.archiveFor(artist.getId());

            // then
            assertThat(conversation.getArchived()).isEqualTo(ArchivedBy.ARTIST);
        }

        @Test
        @DisplayName("Should remain BOTH when archived multiple times by both participants")
        void shouldStayBoth_whenAlreadyBoth() {
            // when
            conversation.archiveFor(hotel.getId());
            conversation.archiveFor(artist.getId());
            conversation.archiveFor(hotel.getId());
            conversation.archiveFor(artist.getId());

            // then
            assertThat(conversation.getArchived()).isEqualTo(ArchivedBy.BOTH);
        }

        @Test
        @DisplayName("Should throw AccessDeniedException when archiving attempt is made by non-participant")
        void shouldThrow_forStranger() {
            // when & then
            assertThatThrownBy(() -> conversation.archiveFor(stranger.getId()))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessage("User is not a participant of this conversation");
        }

        @Test
        @DisplayName("Should not modify archived status when non-participant throws exception")
        void shouldNotChangeArchived_whenStrangerThrows() {
            // when
            assertThatThrownBy(() -> conversation.archiveFor(stranger.getId()))
                    .isInstanceOf(AccessDeniedException.class);

            // then
            assertThat(conversation.getArchived()).isNull();
        }
    }
}