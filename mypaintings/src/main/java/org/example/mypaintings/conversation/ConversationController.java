package org.example.mypaintings.conversation;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.mypaintings.conversation.dto.ConversationsResponse;
import org.example.mypaintings.conversation.dto.MessageResponse;
import org.example.mypaintings.conversation.dto.SendMessageRequest;
import org.example.mypaintings.conversation.streaming.ChatStreamService;
import org.example.mypaintings.auth.CurrentUserId;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;




@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;
    private final ConversationQueryService conversationQueryService;
    private final ChatStreamService chatStreamService;

    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/active")
    public ResponseEntity<List<ConversationsResponse>> getActiveConversations(
           @Parameter(hidden = true) @CurrentUserId Long userId) {
        return ResponseEntity.ok(conversationQueryService.getActiveConversations(userId));
    }


    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/{conversationId}/message-history")
    public ResponseEntity<List<MessageResponse>> getMessages(
            @PathVariable long conversationId,
           @Parameter(hidden = true) @CurrentUserId Long userId) {

        return ResponseEntity.ok(conversationQueryService.getMessages(conversationId, userId));
    }

    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/{conversationId}/message")
    public ResponseEntity<Void> sendMessage(
            @PathVariable long conversationId,
            @RequestBody @Valid SendMessageRequest request,
           @Parameter(hidden = true) @CurrentUserId Long senderId) {

        conversationService.sendMessage(senderId, request.message(), conversationId);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @SecurityRequirement(name = "bearerAuth")
    @PatchMapping("/{conversationId}/archive")
    public ResponseEntity<Void> archive(
            @PathVariable long conversationId,
           @Parameter(hidden = true) @CurrentUserId Long userId) {

        conversationService.archiveConversation(conversationId, userId);
        return ResponseEntity.noContent().build();
    }

    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/{conversationId}/send-delivery-info")
    public ResponseEntity<Void> sendDeliveryInfo(
            @PathVariable long conversationId,
           @Parameter(hidden = true) @CurrentUserId Long hotelId) {
        conversationService.sendDeliveryInfo(hotelId, conversationId);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }


    @SecurityRequirement(name = "bearerAuth")
    @GetMapping(value = "/{conversationId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamMessages(
            @PathVariable long conversationId,
           @Parameter(hidden = true) @CurrentUserId Long userId) {
        return chatStreamService.register(conversationId, userId);
    }

}