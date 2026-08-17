package org.example.mypaintings.conversation;

import jakarta.persistence.EntityNotFoundException;
import org.example.mypaintings.conversation.dto.MessageResponse;
import org.example.mypaintings.conversation.streaming.ChatStreamService;
import org.example.mypaintings.exception.access.UnauthorizedAccessException;
import org.example.mypaintings.painting.Painting;
import org.example.mypaintings.painting.PaintingQueryService;
import org.example.mypaintings.painting.event.PaintingArchivedEvent;
import org.example.mypaintings.painting.placement.event.PlacementStartedEvent;
import org.example.mypaintings.reservation.ReservationOffer;
import org.example.mypaintings.reservation.ReservationQueryService;
import org.example.mypaintings.reservation.event.ReservationRevokedEvent;
import org.example.mypaintings.sale.event.PaintingSoldEvent;
import org.example.mypaintings.user.User;
import org.example.mypaintings.user.UserQueryService;
import org.example.mypaintings.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConversationService Test Suite")
class ConversationServiceTest {

    @Mock
    private UserQueryService userQueryService;

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private ChatStreamService chatStreamService;

    @Mock
    private PaintingQueryService paintingQueryService;

    @Mock
    private ReservationQueryService reservationQueryService;

    @Mock
    private MessageRepository messageRepository;

    @InjectMocks
    private ConversationService conversationService;

    private static final long HOTEL_ID = 1L;
    private static final long ARTIST_ID = 2L;
    private static final long STRANGER_ID = 3L;
    private static final long PAINTING_ID = 10L;
    private static final long OFFER_ID = 100L;
    private static final long CONVERSATION_ID = 500L;

    private User hotel;
    private User artist;
    private Painting painting;
    private ReservationOffer offer;

    @BeforeEach
    void setUp() {
        hotel = new User(UserRole.HOTEL, "GrandHotel", "Paris", 48.8566, 2.3522);
        artist = new User(UserRole.ARTIST, "vanGogh", "Amsterdam", 52.3676, 4.9041);

        setId(hotel, HOTEL_ID);
        setId(artist, ARTIST_ID);

        painting = mock(Painting.class);
        lenient().when(painting.getId()).thenReturn(PAINTING_ID);
        lenient().when(painting.getArtist()).thenReturn(artist);

        offer = mock(ReservationOffer.class);
        lenient().when(offer.getId()).thenReturn(OFFER_ID);
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private static void setId(Object target, Long id) {
        try {
            var field = target.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(target, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private Conversation existingConversation(ArchivedBy archived) {
        Conversation conv = Conversation.builder()
                .painting(painting)
                .hotel(hotel)
                .offer(offer)
                .archived(archived)
                .build();
        setId(conv, CONVERSATION_ID);
        return conv;
    }

    // ---------------------------------------------------------------------
    // createConversation
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("createConversation")
    class CreateConversation {

        @Test
        @DisplayName("creates new conversation when none exists for painting and hotel pair")
        void shouldCreateNewConversation_whenNoneExists() {
            // given
            when(paintingQueryService.getActiveOrThrow(PAINTING_ID)).thenReturn(painting);
            when(userQueryService.getValidHotelOrThrow(HOTEL_ID)).thenReturn(hotel);
            when(conversationRepository.findByPaintingAndHotel(painting, hotel))
                    .thenReturn(Optional.empty());

            Conversation saved = existingConversation(null);
            when(conversationRepository.save(any(Conversation.class))).thenReturn(saved);

            // when
            conversationService.createConversation(HOTEL_ID, PAINTING_ID, offer);

            // then
            ArgumentCaptor<Conversation> captor = ArgumentCaptor.forClass(Conversation.class);
            verify(conversationRepository).save(captor.capture());

            Conversation toSave = captor.getValue();
            assertThat(toSave.getPainting()).isSameAs(painting);
            assertThat(toSave.getHotel()).isSameAs(hotel);
            assertThat(toSave.getOffer()).isSameAs(offer);
            assertThat(toSave.getArchived()).isNull();
            assertThat(toSave.getMessages()).isEmpty();
        }

        @Test
        @DisplayName("does not create new conversation when one already exists")
        void shouldNotCreateNewConversation_whenOneExists() {
            // given
            Conversation existing = existingConversation(null);
            when(paintingQueryService.getActiveOrThrow(PAINTING_ID)).thenReturn(painting);
            when(userQueryService.getValidHotelOrThrow(HOTEL_ID)).thenReturn(hotel);
            when(conversationRepository.findByPaintingAndHotel(painting, hotel))
                    .thenReturn(Optional.of(existing));

            // when
            conversationService.createConversation(HOTEL_ID, PAINTING_ID, offer);

            // then
            verify(conversationRepository, never()).save(any());
        }

        @Test
        @DisplayName("propagates exception when painting is not active")
        void shouldPropagate_whenPaintingNotActive() {
            // given
            when(paintingQueryService.getActiveOrThrow(PAINTING_ID))
                    .thenThrow(new EntityNotFoundException("Painting not active"));

            // when & then
            assertThatThrownBy(() -> conversationService.createConversation(HOTEL_ID, PAINTING_ID, offer))
                    .isInstanceOf(EntityNotFoundException.class);

            verifyNoInteractions(conversationRepository);
        }

        @Test
        @DisplayName("propagates exception when hotel is invalid")
        void shouldPropagate_whenHotelInvalid() {
            // given
            when(paintingQueryService.getActiveOrThrow(PAINTING_ID)).thenReturn(painting);
            when(userQueryService.getValidHotelOrThrow(HOTEL_ID))
                    .thenThrow(new EntityNotFoundException("Hotel not found"));

            // when & then
            assertThatThrownBy(() -> conversationService.createConversation(HOTEL_ID, PAINTING_ID, offer))
                    .isInstanceOf(EntityNotFoundException.class);

            verifyNoInteractions(conversationRepository);
        }
    }

    // ---------------------------------------------------------------------
    // archiveConversation
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("archiveConversation")
    class ArchiveConversation {

        @Test
        @DisplayName("archives conversation for hotel successfully")
        void shouldArchiveForHotel() {
            // given
            Conversation conv = existingConversation(null);
            when(conversationRepository.getOrThrow(CONVERSATION_ID)).thenReturn(conv);

            // when
            conversationService.archiveConversation(CONVERSATION_ID, HOTEL_ID);

            // then
            assertThat(conv.getArchived()).isEqualTo(ArchivedBy.HOTEL);
        }

        @Test
        @DisplayName("archives conversation for artist successfully")
        void shouldArchiveForArtist() {
            // given
            Conversation conv = existingConversation(null);
            when(conversationRepository.getOrThrow(CONVERSATION_ID)).thenReturn(conv);

            // when
            conversationService.archiveConversation(CONVERSATION_ID, ARTIST_ID);

            // then
            assertThat(conv.getArchived()).isEqualTo(ArchivedBy.ARTIST);
        }

        @Test
        @DisplayName("throws exception for non-participant stranger")
        void shouldThrow_forStranger() {
            // given
            Conversation conv = existingConversation(null);
            when(conversationRepository.getOrThrow(CONVERSATION_ID)).thenReturn(conv);

            // when & then
            assertThatThrownBy(() -> conversationService.archiveConversation(CONVERSATION_ID, STRANGER_ID))
                    .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);

            assertThat(conv.getArchived()).isNull();
        }

        @Test
        @DisplayName("propagates exception when conversation does not exist")
        void shouldPropagate_whenConversationMissing() {
            // given
            when(conversationRepository.getOrThrow(CONVERSATION_ID))
                    .thenThrow(new EntityNotFoundException("Conversation not found"));

            // when & then
            assertThatThrownBy(() -> conversationService.archiveConversation(CONVERSATION_ID, HOTEL_ID))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    // ---------------------------------------------------------------------
    // sendMessage
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("sendMessage")
    class SendMessage {

        @Test
        @DisplayName("routes message from hotel to artist correctly")
        void shouldRouteMessage_fromHotelToArtist() {
            // given
            Conversation conv = existingConversation(null);
            when(conversationRepository.getOrThrow(CONVERSATION_ID)).thenReturn(conv);
            when(userQueryService.getUserReference(HOTEL_ID)).thenReturn(hotel);
            when(userQueryService.getUserReference(ARTIST_ID)).thenReturn(artist);
            when(messageRepository.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));

            // when
            conversationService.sendMessage(HOTEL_ID, "hello", CONVERSATION_ID);

            // then
            ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
            verify(messageRepository).save(captor.capture());
            Message saved = captor.getValue();

            assertThat(saved.getSender()).isSameAs(hotel);
            assertThat(saved.getReceiver()).isSameAs(artist);
            assertThat(saved.getMessageContent()).isEqualTo("hello");
        }

        @Test
        @DisplayName("routes message from artist to hotel correctly")
        void shouldRouteMessage_fromArtistToHotel() {
            // given
            Conversation conv = existingConversation(null);
            when(conversationRepository.getOrThrow(CONVERSATION_ID)).thenReturn(conv);
            when(userQueryService.getUserReference(ARTIST_ID)).thenReturn(artist);
            when(userQueryService.getUserReference(HOTEL_ID)).thenReturn(hotel);
            when(messageRepository.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));

            // when
            conversationService.sendMessage(ARTIST_ID, "hi", CONVERSATION_ID);

            // then
            ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
            verify(messageRepository).save(captor.capture());

            assertThat(captor.getValue().getSender()).isSameAs(artist);
            assertThat(captor.getValue().getReceiver()).isSameAs(hotel);
        }

        @Test
        @DisplayName("notifies chat stream with DTO after saving message")
        void shouldNotifyStream() {
            // given
            Conversation conv = existingConversation(null);
            when(conversationRepository.getOrThrow(CONVERSATION_ID)).thenReturn(conv);
            when(userQueryService.getUserReference(HOTEL_ID)).thenReturn(hotel);
            when(userQueryService.getUserReference(ARTIST_ID)).thenReturn(artist);
            when(messageRepository.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));

            // when
            conversationService.sendMessage(HOTEL_ID, "hello", CONVERSATION_ID);

            // then
            ArgumentCaptor<MessageResponse> captor = ArgumentCaptor.forClass(MessageResponse.class);
            verify(chatStreamService).notifyNewMessage(eq(CONVERSATION_ID), captor.capture());

            MessageResponse dto = captor.getValue();
            assertThat(dto.senderId()).isEqualTo(HOTEL_ID);
            assertThat(dto.receiverId()).isEqualTo(ARTIST_ID);
            assertThat(dto.message()).isEqualTo("hello");
            assertThat(dto.sentDate()).isNotNull();
        }

        @Test
        @DisplayName("does not notify stream when database save fails")
        void shouldNotNotifyStream_whenSaveFails() {
            // given
            Conversation conv = existingConversation(null);
            when(conversationRepository.getOrThrow(CONVERSATION_ID)).thenReturn(conv);
            when(userQueryService.getUserReference(HOTEL_ID)).thenReturn(hotel);
            when(userQueryService.getUserReference(ARTIST_ID)).thenReturn(artist);
            when(messageRepository.save(any(Message.class)))
                    .thenThrow(new RuntimeException("db error"));

            // when & then
            assertThatThrownBy(() -> conversationService.sendMessage(HOTEL_ID, "hello", CONVERSATION_ID))
                    .isInstanceOf(RuntimeException.class);

            verifyNoInteractions(chatStreamService);
        }
    }

    // ---------------------------------------------------------------------
    // sendDeliveryInfo
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("sendDeliveryInfo")
    class SendDeliveryInfo {

        @Test
        @DisplayName("creates automated message with hotel address successfully")
        void shouldCreateAutomatedMessage_withHotelAddress() {
            // given
            Conversation conv = existingConversation(null);
            when(conversationRepository.getOrThrow(CONVERSATION_ID)).thenReturn(conv);
            when(userQueryService.getValidHotelOrThrow(HOTEL_ID)).thenReturn(hotel);
            when(userQueryService.getUserReference(HOTEL_ID)).thenReturn(hotel);
            when(userQueryService.getUserReference(ARTIST_ID)).thenReturn(artist);
            when(messageRepository.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));

            // when
            conversationService.sendDeliveryInfo(HOTEL_ID, CONVERSATION_ID);

            // then
            ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
            verify(messageRepository).save(captor.capture());

            Message saved = captor.getValue();
            assertThat(saved.getSender()).isSameAs(hotel);
            assertThat(saved.getReceiver()).isSameAs(artist);
            assertThat(saved.getMessageContent())
                    .isEqualTo("Automatically generated message, here are my delivery details: " + hotel.getFullAddress());
        }

        @Test
        @DisplayName("propagates exception when hotel is invalid")
        void shouldPropagate_whenHotelInvalid() {
            // given
            Conversation conv = existingConversation(null);
            when(conversationRepository.getOrThrow(CONVERSATION_ID)).thenReturn(conv);
            when(userQueryService.getValidHotelOrThrow(HOTEL_ID))
                    .thenThrow(new EntityNotFoundException("Hotel not found"));

            // when & then
            assertThatThrownBy(() -> conversationService.sendDeliveryInfo(HOTEL_ID, CONVERSATION_ID))
                    .isInstanceOf(EntityNotFoundException.class);

            verifyNoInteractions(messageRepository);
        }
    }

    // ---------------------------------------------------------------------
    // archiveConversationsForPainting
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("archiveConversationsForPainting")
    class ArchiveConversationsForPainting {

        @Test
        @DisplayName("sets BOTH archived status for all conversations of the painting")
        void shouldSetBoth_forAllConversations() {
            // given
            Conversation c1 = existingConversation(ArchivedBy.HOTEL);
            Conversation c2 = existingConversation(null);
            when(conversationRepository.findAllByPainting(painting)).thenReturn(List.of(c1, c2));

            // when
            conversationService.archiveConversationsForPainting(painting);

            // then
            assertThat(c1.getArchived()).isEqualTo(ArchivedBy.BOTH);
            assertThat(c2.getArchived()).isEqualTo(ArchivedBy.BOTH);
            verify(conversationRepository).saveAll(List.of(c1, c2));
        }

        @Test
        @DisplayName("does nothing when no conversations exist for painting")
        void shouldDoNothing_whenNoConversations() {
            // given
            when(conversationRepository.findAllByPainting(painting)).thenReturn(List.of());

            // when
            conversationService.archiveConversationsForPainting(painting);

            // then
            verify(conversationRepository).saveAll(List.of());
        }
    }

    // ---------------------------------------------------------------------
    // Event Handlers
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("Event Handlers")
    class EventHandlers {

        @Test
        @DisplayName("handlePaintingDeleted archives conversations")
        void handlePaintingDeleted_shouldArchive() {
            // given
            when(paintingQueryService.getOrThrow(PAINTING_ID)).thenReturn(painting);
            Conversation c = existingConversation(ArchivedBy.HOTEL);
            when(conversationRepository.findAllByPainting(painting)).thenReturn(List.of(c));

            // when
            conversationService.handlePaintingDeleted(new PaintingArchivedEvent(PAINTING_ID));

            // then
            assertThat(c.getArchived()).isEqualTo(ArchivedBy.BOTH);
            verify(conversationRepository).saveAll(List.of(c));
        }

        @Test
        @DisplayName("handlePaintingSold archives conversations")
        void handlePaintingSold_shouldArchive() {
            // given
            when(paintingQueryService.getOrThrow(PAINTING_ID)).thenReturn(painting);
            Conversation c = existingConversation(null);
            when(conversationRepository.findAllByPainting(painting)).thenReturn(List.of(c));

            // when
            conversationService.handlePaintingSold(new PaintingSoldEvent(PAINTING_ID, false));

            // then
            assertThat(c.getArchived()).isEqualTo(ArchivedBy.BOTH);
        }

        @Test
        @DisplayName("handleReservationRevoked archives conversations")
        void handleReservationRevoked_shouldArchive() {
            // given
            when(paintingQueryService.getOrThrow(PAINTING_ID)).thenReturn(painting);
            Conversation c = existingConversation(ArchivedBy.ARTIST);
            when(conversationRepository.findAllByPainting(painting)).thenReturn(List.of(c));

            // when
            conversationService.handleReservationRevoked(new ReservationRevokedEvent(PAINTING_ID, PAINTING_ID));

            // then
            assertThat(c.getArchived()).isEqualTo(ArchivedBy.BOTH);
        }

        @Test
        @DisplayName("handlePlacementStarted creates conversation from accepted offer")
        void handlePlacementStarted_shouldCreateConversation() {
            // given
            when(reservationQueryService.getValidOrThrow(OFFER_ID)).thenReturn(offer);
            when(paintingQueryService.getActiveOrThrow(PAINTING_ID)).thenReturn(painting);
            when(userQueryService.getValidHotelOrThrow(HOTEL_ID)).thenReturn(hotel);
            when(conversationRepository.findByPaintingAndHotel(painting, hotel))
                    .thenReturn(Optional.empty());

            Conversation saved = existingConversation(null);
            when(conversationRepository.save(any(Conversation.class))).thenReturn(saved);

            // when
            conversationService.handlePlacementStarted(
                    new PlacementStartedEvent(PAINTING_ID, HOTEL_ID, OFFER_ID));

            // then
            ArgumentCaptor<Conversation> captor = ArgumentCaptor.forClass(Conversation.class);
            verify(conversationRepository).save(captor.capture());

            assertThat(captor.getValue().getOffer()).isSameAs(offer);
            assertThat(captor.getValue().getPainting()).isSameAs(painting);
            assertThat(captor.getValue().getHotel()).isSameAs(hotel);
        }

        @Test
        @DisplayName("handlePlacementStarted propagates exception when offer is invalid")
        void handlePlacementStarted_shouldPropagate_whenOfferInvalid() {
            // given
            when(reservationQueryService.getValidOrThrow(OFFER_ID))
                    .thenThrow(new EntityNotFoundException("Offer not valid"));

            // when & then
            assertThatThrownBy(() -> conversationService.handlePlacementStarted(
                    new PlacementStartedEvent(HOTEL_ID, PAINTING_ID, OFFER_ID)))
                    .isInstanceOf(EntityNotFoundException.class);

            verifyNoInteractions(conversationRepository);
        }
    }

    // ---------------------------------------------------------------------
    // internalCreateMessage (tested via sendMessage)
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("internalCreateMessage (via sendMessage)")
    class InternalCreateMessage {

        @Test
        @DisplayName("adds message to Conversation entity before saving")
        void shouldAddMessageToConversation() {
            // given
            Conversation conv = existingConversation(null);
            when(conversationRepository.getOrThrow(CONVERSATION_ID)).thenReturn(conv);
            when(userQueryService.getUserReference(HOTEL_ID)).thenReturn(hotel);
            when(userQueryService.getUserReference(ARTIST_ID)).thenReturn(artist);
            when(messageRepository.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));

            // when
            conversationService.sendMessage(HOTEL_ID, "hello", CONVERSATION_ID);

            // then
            assertThat(conv.getMessages()).hasSize(1);
            assertThat(conv.getMessages().getFirst().getMessageContent()).isEqualTo("hello");
        }

        @Test
        @DisplayName("propagates validation exception from Conversation entity")
        void shouldPropagate_whenConversationValidationFails() {
            // given
            Conversation conv = existingConversation(null);
            when(conversationRepository.getOrThrow(CONVERSATION_ID)).thenReturn(conv);

            User stranger = new User(UserRole.HOTEL, "stranger", "Nowhere", 0.0, 0.0);
            setId(stranger, STRANGER_ID);

            when(userQueryService.getUserReference(STRANGER_ID)).thenReturn(stranger);
            when(userQueryService.getUserReference(HOTEL_ID)).thenReturn(hotel);

            // when & then
            assertThatThrownBy(() -> conversationService.sendMessage(STRANGER_ID, "hi", CONVERSATION_ID))
                    .isInstanceOf(UnauthorizedAccessException.class);

            verify(messageRepository, never()).save(any());
            verifyNoInteractions(chatStreamService);
        }
    }

    // ---------------------------------------------------------------------
    // Repository Contract
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("Repository Contract")
    class RepositoryContract {

        @Test
        @DisplayName("archiveConversation uses getOrThrow instead of findById")
        void shouldUseGetOrThrow() {
            // given
            Conversation conv = existingConversation(null);
            when(conversationRepository.getOrThrow(CONVERSATION_ID)).thenReturn(conv);

            // when
            conversationService.archiveConversation(CONVERSATION_ID, HOTEL_ID);

            // then
            verify(conversationRepository).getOrThrow(CONVERSATION_ID);
            verify(conversationRepository, never()).findById(anyLong());
        }

        @Test
        @DisplayName("sendMessage uses getOrThrow for conversation retrieval")
        void sendMessage_shouldUseGetOrThrow() {
            // given
            Conversation conv = existingConversation(null);
            when(conversationRepository.getOrThrow(CONVERSATION_ID)).thenReturn(conv);
            when(userQueryService.getUserReference(HOTEL_ID)).thenReturn(hotel);
            when(userQueryService.getUserReference(ARTIST_ID)).thenReturn(artist);
            when(messageRepository.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));

            // when
            conversationService.sendMessage(HOTEL_ID, "hi", CONVERSATION_ID);

            // then
            verify(conversationRepository).getOrThrow(CONVERSATION_ID);
        }
    }
}