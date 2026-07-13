package org.example.mypaintings.conversation.streaming;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class SseEmitterFactory {
    public SseEmitter createEmitter(long timeout) {
        return new SseEmitter(timeout);
    }
}