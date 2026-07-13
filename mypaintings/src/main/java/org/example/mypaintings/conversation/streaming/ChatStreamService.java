package org.example.mypaintings.conversation.streaming;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.mypaintings.conversation.ConversationRepository;
import org.example.mypaintings.conversation.dto.MessageResponse;
import org.example.mypaintings.conversation.Conversation;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.concurrent.CopyOnWriteArrayList;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;



@Service
@AllArgsConstructor
@Slf4j
public class ChatStreamService {


    private final SseEmitterFactory emitterFactory;
    private final ConversationRepository conversationRepository;

    private final Map<Long, List<SseEmitter>> emittersMap = new ConcurrentHashMap<>();

    public SseEmitter register(long conversationId, long userId) {
        Conversation conv = conversationRepository.getOrThrow(conversationId);

        conv.validateParticipantOrThrow(userId);

        SseEmitter emitter = emitterFactory.createEmitter(2 * 60 * 1000L);

        emittersMap.computeIfAbsent(conversationId, _ -> new CopyOnWriteArrayList<>()).add(emitter);

        log.debug("Registered new SSE emitter for user: {} in conversation: {}", userId, conversationId);

        emitter.onCompletion(() -> removeEmitter(conversationId, emitter));
        emitter.onTimeout(() -> removeEmitter(conversationId, emitter));
        emitter.onError(_ -> removeEmitter(conversationId, emitter));

        return emitter;
    }

    public void notifyNewMessage(long conversationId, MessageResponse message) {
        List<SseEmitter> emitters = emittersMap.get(conversationId);
        if (emitters == null) return;

        List<SseEmitter> deadEmitters = new ArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("message").data(message));
            } catch (IOException e) {
                log.debug("Failed to send SSE message to client in conversation: {}. Removing dead emitter. Reason: {}", conversationId, e.getMessage());
                deadEmitters.add(emitter);
            }
        }
        emitters.removeAll(deadEmitters);
    }

    private void removeEmitter(long conversationId, SseEmitter emitter) {
        List<SseEmitter> emitters = emittersMap.get(conversationId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                emittersMap.remove(conversationId);
                log.debug("All SSE emitters removed for conversation: {}. Cleaning up map.", conversationId);
            }
        }
    }
}
