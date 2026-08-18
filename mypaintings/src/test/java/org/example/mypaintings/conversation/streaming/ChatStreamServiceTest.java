package org.example.mypaintings.conversation.streaming;

import org.example.mypaintings.conversation.Conversation;
import org.example.mypaintings.conversation.ConversationRepository;
import org.example.mypaintings.conversation.dto.MessageResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ChatStreamService Test Suite")
class ChatStreamServiceTest {

    @Mock
    private SseEmitterFactory emitterFactory;

    @Mock
    private ConversationRepository conversationRepository;

    @InjectMocks
    private ChatStreamService chatStreamService;

    private static final long CONVERSATION_ID = 1L;
    private static final long USER_ID = 42L;
    private static final long TIMEOUT_MS = 2 * 60 * 1000L;

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private SseEmitter mockEmitter() {
        return mock(SseEmitter.class);
    }

    private Conversation mockConversationForUser(long userId) {
        Conversation conv = mock(Conversation.class);
        doNothing().when(conv).validateParticipantOrThrow(userId);
        return conv;
    }

    private MessageResponse message() {
        return new MessageResponse(1L, USER_ID, "hello", java.time.Instant.now());
    }

    // ------------------------------------------------------------------
    // register
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("register")
    class Register {

        @Test
        @DisplayName("creates emitter with 2-minute timeout and stores it in map")
        void register_shouldCreateAndStoreEmitter() {
            // given
            Conversation conv = mockConversationForUser(USER_ID);
            SseEmitter emitter = mockEmitter();

            when(conversationRepository.getOrThrow(CONVERSATION_ID)).thenReturn(conv);
            when(emitterFactory.createEmitter(TIMEOUT_MS)).thenReturn(emitter);

            // when
            SseEmitter result = chatStreamService.register(CONVERSATION_ID, USER_ID);

            // then
            assertThat(result).isSameAs(emitter);
            verify(conversationRepository).getOrThrow(CONVERSATION_ID);
            verify(conv).validateParticipantOrThrow(USER_ID);
            verify(emitterFactory).createEmitter(TIMEOUT_MS);
            verify(emitter).onCompletion(any());
            verify(emitter).onTimeout(any());
            verify(emitter).onError(any());
        }

        @Test
        @DisplayName("propagates AccessDeniedException when user is not a participant")
        void register_shouldThrow_whenNotParticipant() {
            // given
            Conversation conv = mock(Conversation.class);
            doThrow(new AccessDeniedException("not a participant"))
                    .when(conv).validateParticipantOrThrow(USER_ID);

            when(conversationRepository.getOrThrow(CONVERSATION_ID)).thenReturn(conv);

            // when & then
            assertThatThrownBy(() -> chatStreamService.register(CONVERSATION_ID, USER_ID))
                    .isInstanceOf(AccessDeniedException.class);

            verifyNoInteractions(emitterFactory);
        }

        @Test
        @DisplayName("propagates exception when conversation does not exist")
        void register_shouldThrow_whenConversationNotFound() {
            // given
            when(conversationRepository.getOrThrow(CONVERSATION_ID))
                    .thenThrow(new IllegalStateException("not found"));

            // when & then
            assertThatThrownBy(() -> chatStreamService.register(CONVERSATION_ID, USER_ID))
                    .isInstanceOf(IllegalStateException.class);

            verifyNoInteractions(emitterFactory);
        }

        @Test
        @DisplayName("registers multiple emitters for the same conversation successfully")
        void register_shouldStoreTwoEmitters_forSameConversation() throws Exception {
            // given
            Conversation conv = mockConversationForUser(USER_ID);
            SseEmitter e1 = mockEmitter();
            SseEmitter e2 = mockEmitter();

            when(conversationRepository.getOrThrow(CONVERSATION_ID)).thenReturn(conv);
            when(emitterFactory.createEmitter(TIMEOUT_MS)).thenReturn(e1, e2);

            // when
            chatStreamService.register(CONVERSATION_ID, USER_ID);
            chatStreamService.register(CONVERSATION_ID, USER_ID);

            chatStreamService.notifyNewMessage(CONVERSATION_ID, message());

            // then
            verify(e1).send(any(SseEmitter.SseEventBuilder.class));
            verify(e2).send(any(SseEmitter.SseEventBuilder.class));
        }
    }

    // ------------------------------------------------------------------
    // notifyNewMessage
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("notifyNewMessage")
    class NotifyNewMessage {

        @Test
        @DisplayName("sends event to all conversation emitters")
        void notifyNewMessage_shouldSendToAllEmitters() throws Exception {
            // given
            Conversation conv = mockConversationForUser(USER_ID);
            SseEmitter e1 = mockEmitter();
            SseEmitter e2 = mockEmitter();
            when(conversationRepository.getOrThrow(CONVERSATION_ID)).thenReturn(conv);
            when(emitterFactory.createEmitter(TIMEOUT_MS)).thenReturn(e1, e2);
            chatStreamService.register(CONVERSATION_ID, USER_ID);
            chatStreamService.register(CONVERSATION_ID, USER_ID);

            // when
            chatStreamService.notifyNewMessage(CONVERSATION_ID, message());

            // then
            verify(e1).send(any(SseEmitter.SseEventBuilder.class));
            verify(e2).send(any(SseEmitter.SseEventBuilder.class));
        }

        @Test
        @DisplayName("does nothing when no emitters exist for conversation")
        void notifyNewMessage_shouldDoNothing_whenNoEmitters() {
            // when
            chatStreamService.notifyNewMessage(CONVERSATION_ID, message());

            // then
            verifyNoInteractions(emitterFactory);
        }

        @Test
        @DisplayName("removes dead emitter on IOException")
        void notifyNewMessage_shouldRemoveDeadEmitter_onIOException() throws Exception {
            // given
            Conversation conv = mockConversationForUser(USER_ID);
            SseEmitter dead = mockEmitter();
            SseEmitter alive = mockEmitter();
            when(conversationRepository.getOrThrow(CONVERSATION_ID)).thenReturn(conv);
            when(emitterFactory.createEmitter(TIMEOUT_MS)).thenReturn(dead, alive);
            chatStreamService.register(CONVERSATION_ID, USER_ID);
            chatStreamService.register(CONVERSATION_ID, USER_ID);

            doThrow(new IOException("broken pipe"))
                    .when(dead).send(any(SseEmitter.SseEventBuilder.class));

            // when
            chatStreamService.notifyNewMessage(CONVERSATION_ID, message());
            chatStreamService.notifyNewMessage(CONVERSATION_ID, message());

            // then
            verify(dead, times(1)).send(any(SseEmitter.SseEventBuilder.class));
            verify(alive, times(2)).send(any(SseEmitter.SseEventBuilder.class));
        }

        @Test
        @DisplayName("cleans up map completely when all emitters are dead")
        void notifyNewMessage_shouldCleanupMap_whenAllEmittersDead() throws Exception {
            // given
            Conversation conv = mockConversationForUser(USER_ID);
            SseEmitter e1 = mockEmitter();
            when(conversationRepository.getOrThrow(CONVERSATION_ID)).thenReturn(conv);
            when(emitterFactory.createEmitter(TIMEOUT_MS)).thenReturn(e1);
            chatStreamService.register(CONVERSATION_ID, USER_ID);

            doThrow(new IOException("broken pipe"))
                    .when(e1).send(any(SseEmitter.SseEventBuilder.class));

            // when
            chatStreamService.notifyNewMessage(CONVERSATION_ID, message());
            chatStreamService.notifyNewMessage(CONVERSATION_ID, message());

            // then
            verify(e1, times(1)).send(any(SseEmitter.SseEventBuilder.class));
        }
    }

    // ------------------------------------------------------------------
    // Emitter Lifecycle Callbacks (Removal)
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Emitter Lifecycle Callbacks")
    class LifecycleCallbacks {

        @Test
        @DisplayName("onCompletion removes emitter from map")
        void onCompletion_shouldRemoveEmitter() throws Exception {
            // given
            Conversation conv = mockConversationForUser(USER_ID);
            SseEmitter e1 = mockEmitter();
            when(conversationRepository.getOrThrow(CONVERSATION_ID)).thenReturn(conv);
            when(emitterFactory.createEmitter(TIMEOUT_MS)).thenReturn(e1);

            chatStreamService.register(CONVERSATION_ID, USER_ID);

            ArgumentCaptor<Runnable> completionCaptor = ArgumentCaptor.forClass(Runnable.class);
            verify(e1).onCompletion(completionCaptor.capture());

            // when
            completionCaptor.getValue().run();
            chatStreamService.notifyNewMessage(CONVERSATION_ID, message());

            // then
            verify(e1, never()).send(any(SseEmitter.SseEventBuilder.class));
        }

        @Test
        @DisplayName("onTimeout removes emitter from map")
        void onTimeout_shouldRemoveEmitter() throws Exception {
            // given
            Conversation conv = mockConversationForUser(USER_ID);
            SseEmitter e1 = mockEmitter();
            when(conversationRepository.getOrThrow(CONVERSATION_ID)).thenReturn(conv);
            when(emitterFactory.createEmitter(TIMEOUT_MS)).thenReturn(e1);

            chatStreamService.register(CONVERSATION_ID, USER_ID);

            ArgumentCaptor<Runnable> timeoutCaptor = ArgumentCaptor.forClass(Runnable.class);
            verify(e1).onTimeout(timeoutCaptor.capture());

            // when
            timeoutCaptor.getValue().run();
            chatStreamService.notifyNewMessage(CONVERSATION_ID, message());

            // then
            verify(e1, never()).send(any(SseEmitter.SseEventBuilder.class));
        }

        @Test
        @DisplayName("onError removes emitter from map")
        void onError_shouldRemoveEmitter() throws Exception {
            // given
            Conversation conv = mockConversationForUser(USER_ID);
            SseEmitter e1 = mockEmitter();
            when(conversationRepository.getOrThrow(CONVERSATION_ID)).thenReturn(conv);
            when(emitterFactory.createEmitter(TIMEOUT_MS)).thenReturn(e1);

            chatStreamService.register(CONVERSATION_ID, USER_ID);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<java.util.function.Consumer<Throwable>> errorCaptor =
                    ArgumentCaptor.forClass(java.util.function.Consumer.class);
            verify(e1).onError(errorCaptor.capture());

            // when
            errorCaptor.getValue().accept(new RuntimeException("boom"));
            chatStreamService.notifyNewMessage(CONVERSATION_ID, message());

            // then
            verify(e1, never()).send(any(SseEmitter.SseEventBuilder.class));
        }
    }
}
