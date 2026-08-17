package org.example.mypaintings.conversation;

import org.example.mypaintings.conversation.dto.ConversationsResponse;
import org.example.mypaintings.conversation.dto.MessageResponse;
import org.example.mypaintings.exception.notfound.ResourceNotFoundException;
import org.example.mypaintings.painting.Painting;
import org.example.mypaintings.painting.attachedimages.FileUrlService;
import org.example.mypaintings.user.User;
import org.example.mypaintings.user.UserQueryService;
import org.example.mypaintings.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConversationQueryService Test Suite")
class ConversationQueryServiceTest {

    @Mock private UserQueryService userQueryService;
    @Mock private ConversationRepository conversationRepository;
    @Mock private FileUrlService fileUrlService;
    @Mock private MessageRepository messageRepository;

    @InjectMocks private ConversationQueryService service;

    private User hotel;
    private User artist;
    private Painting painting;
    private Conversation conversation;

    @BeforeEach
    void setUp() {
        hotel = mock(User.class);
        artist = mock(User.class);
        painting = mock(Painting.class);
        conversation = mock(Conversation.class);
    }

    // ------------------------------------------------------------------
    // getActiveConversations
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("getActiveConversations")
    class GetActiveConversations {

        @Test
        @DisplayName("fetches conversations for HOTEL role and maps other user as artist")
        void getActiveConversations_forHotel_usesHotelQueryAndMapsOtherUserAsArtist() {
            // given
            when(artist.getId()).thenReturn(1L);
            when(artist.getNickname()).thenReturn("artist1");
            when(painting.getArtist()).thenReturn(artist);
            when(painting.getTitle()).thenReturn("Sunset");
            when(painting.getFirstImageFileKey()).thenReturn("key-1.jpg");
            when(conversation.getId()).thenReturn(100L);
            when(conversation.getPainting()).thenReturn(painting);

            when(userQueryService.getUserRole(2L)).thenReturn(UserRole.HOTEL);
            when(conversationRepository.findActiveByHotelIdWithDetails(2L)).thenReturn(List.of(conversation));
            when(fileUrlService.buildUrl("key-1.jpg")).thenReturn("http://cdn/key-1.jpg");

            // when
            List<ConversationsResponse> result = service.getActiveConversations(2L);

            // then
            assertThat(result).hasSize(1);
            ConversationsResponse dto = result.getFirst();
            assertThat(dto.id()).isEqualTo(100L);
            assertThat(dto.artistId()).isEqualTo(1L);
            assertThat(dto.paintingUrl()).isEqualTo("http://cdn/key-1.jpg");
            assertThat(dto.paintingTitle()).isEqualTo("Sunset");
            assertThat(dto.otherUserNickname()).isEqualTo("artist1");

            verify(conversationRepository).findActiveByHotelIdWithDetails(2L);
            verify(conversationRepository, never()).findActiveByPaintingArtistIdWithDetails(anyLong());
        }

        @Test
        @DisplayName("fetches conversations for ARTIST role and maps other user as hotel")
        void getActiveConversations_forArtist_usesArtistQueryAndMapsOtherUserAsHotel() {
            // given
            when(artist.getId()).thenReturn(1L);
            when(hotel.getNickname()).thenReturn("hotel1");
            when(painting.getArtist()).thenReturn(artist);
            when(painting.getTitle()).thenReturn("Sunset");
            when(painting.getFirstImageFileKey()).thenReturn("key-1.jpg");
            when(conversation.getId()).thenReturn(100L);
            when(conversation.getPainting()).thenReturn(painting);
            when(conversation.getHotel()).thenReturn(hotel);

            when(userQueryService.getUserRole(1L)).thenReturn(UserRole.ARTIST);
            when(conversationRepository.findActiveByPaintingArtistIdWithDetails(1L)).thenReturn(List.of(conversation));
            when(fileUrlService.buildUrl("key-1.jpg")).thenReturn("http://cdn/key-1.jpg");

            // when
            List<ConversationsResponse> result = service.getActiveConversations(1L);

            // then
            assertThat(result).hasSize(1);
            ConversationsResponse dto = result.getFirst();
            assertThat(dto.id()).isEqualTo(100L);
            assertThat(dto.artistId()).isEqualTo(1L);
            assertThat(dto.paintingUrl()).isEqualTo("http://cdn/key-1.jpg");
            assertThat(dto.paintingTitle()).isEqualTo("Sunset");
            assertThat(dto.otherUserNickname()).isEqualTo("hotel1");

            verify(conversationRepository).findActiveByPaintingArtistIdWithDetails(1L);
            verify(conversationRepository, never()).findActiveByHotelIdWithDetails(anyLong());
        }

        @Test
        @DisplayName("returns empty list when no conversations exist")
        void getActiveConversations_returnsEmpty_whenNoConversations() {
            // given
            when(userQueryService.getUserRole(2L)).thenReturn(UserRole.HOTEL);
            when(conversationRepository.findActiveByHotelIdWithDetails(2L)).thenReturn(List.of());

            // when
            List<ConversationsResponse> result = service.getActiveConversations(2L);

            // then
            assertThat(result).isEmpty();
            verifyNoInteractions(fileUrlService);
        }

        @Test
        @DisplayName("maps null painting URL when painting has no attachments")
        void getActiveConversations_mapsNullPaintingUrl_whenPaintingHasNoAttachments() {
            // given
            when(artist.getId()).thenReturn(1L);
            when(artist.getNickname()).thenReturn("artist1");
            when(painting.getArtist()).thenReturn(artist);
            when(painting.getTitle()).thenReturn("No Image");
            when(painting.getFirstImageFileKey()).thenReturn(null);
            when(conversation.getId()).thenReturn(101L);
            when(conversation.getPainting()).thenReturn(painting);

            when(userQueryService.getUserRole(2L)).thenReturn(UserRole.HOTEL);
            when(conversationRepository.findActiveByHotelIdWithDetails(2L)).thenReturn(List.of(conversation));
            when(fileUrlService.buildUrl(null)).thenReturn(null);

            // when
            List<ConversationsResponse> result = service.getActiveConversations(2L);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.getFirst().paintingUrl()).isNull();
        }

        @Test
        @DisplayName("throws IllegalStateException when user role is unsupported")
        void getActiveConversations_throws_whenRoleUnsupported() {
            // given
            UserRole unsupportedRole = mock(UserRole.class);
            when(userQueryService.getUserRole(3L)).thenReturn(unsupportedRole);

            // when & then
            assertThatThrownBy(() -> service.getActiveConversations(3L))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Unsupported user role");

            verifyNoInteractions(conversationRepository, fileUrlService);
        }
    }

    // ------------------------------------------------------------------
    // getMessages
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("getMessages")
    class GetMessages {

        @Test
        @DisplayName("returns mapped messages when user is a participant")
        void getMessages_returnsMappedMessages_whenUserIsParticipant() {
            // given
            when(artist.getId()).thenReturn(1L);
            when(hotel.getId()).thenReturn(2L);

            Message msg = mock(Message.class);
            when(msg.getSender()).thenReturn(artist);
            when(msg.getReceiver()).thenReturn(hotel);
            when(msg.getMessageContent()).thenReturn("hello");
            when(msg.getCreatedAt()).thenReturn(Instant.parse("2026-01-01T10:00:00Z"));

            when(conversationRepository.getOrThrow(100L)).thenReturn(conversation);
            doNothing().when(conversation).validateParticipantOrThrow(1L);
            when(messageRepository.findByConversationIdOrderByCreatedAtAsc(100L)).thenReturn(List.of(msg));

            // when
            List<MessageResponse> result = service.getMessages(100L, 1L);

            // then
            assertThat(result).hasSize(1);
            MessageResponse dto = result.getFirst();
            assertThat(dto.senderId()).isEqualTo(1L);
            assertThat(dto.receiverId()).isEqualTo(2L);
            assertThat(dto.message()).isEqualTo("hello");
            assertThat(dto.sentDate()).isEqualTo(Instant.parse("2026-01-01T10:00:00Z"));

            verify(conversation).validateParticipantOrThrow(1L);
            verify(messageRepository).findByConversationIdOrderByCreatedAtAsc(100L);
        }

        @Test
        @DisplayName("returns empty list when conversation has no messages")
        void getMessages_returnsEmpty_whenConversationHasNoMessages() {
            // given
            when(conversationRepository.getOrThrow(100L)).thenReturn(conversation);
            doNothing().when(conversation).validateParticipantOrThrow(1L);
            when(messageRepository.findByConversationIdOrderByCreatedAtAsc(100L)).thenReturn(List.of());

            // when
            List<MessageResponse> result = service.getMessages(100L, 1L);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("throws AccessDeniedException when user is not a participant")
        void getMessages_throws_whenUserIsNotParticipant() {
            // given
            when(conversationRepository.getOrThrow(100L)).thenReturn(conversation);
            doThrow(new AccessDeniedException("not a participant"))
                    .when(conversation).validateParticipantOrThrow(99L);

            // when & then
            assertThatThrownBy(() -> service.getMessages(100L, 99L))
                    .isInstanceOf(AccessDeniedException.class);

            verifyNoInteractions(messageRepository);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when conversation does not exist")
        void getMessages_throws_whenConversationDoesNotExist() {
            // given
            when(conversationRepository.getOrThrow(999L))
                    .thenThrow(new ResourceNotFoundException(Conversation.class, 999L));

            // when & then
            assertThatThrownBy(() -> service.getMessages(999L, 1L))
                    .isInstanceOf(ResourceNotFoundException.class);

            verifyNoInteractions(messageRepository);
        }
    }
}