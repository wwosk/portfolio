package org.example.mypaintings.conversation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.mypaintings.conversation.dto.ConversationsResponse;
import org.example.mypaintings.conversation.dto.MessageResponse;
import org.example.mypaintings.painting.attachedimages.FileUrlService;
import org.example.mypaintings.user.UserQueryService;
import org.example.mypaintings.user.UserRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;



@Slf4j
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Service
public class ConversationQueryService {

    private final UserQueryService userQueryService;
    private final ConversationRepository conversationRepository;
    private final FileUrlService fileUrlService;
    private final MessageRepository messageRepository;

    public List<ConversationsResponse> getActiveConversations(long userId) {
        log.debug("Fetching active conversations for user ID: {}", userId);

        UserRole role = userQueryService.getUserRole(userId);
        List<Conversation> conversations;
        if (role == UserRole.HOTEL) {
            conversations = conversationRepository.findActiveByHotelIdWithDetails(userId);
        } else if (role == UserRole.ARTIST) {
            conversations = conversationRepository.findActiveByPaintingArtistIdWithDetails(userId);
        } else {
            throw new IllegalStateException("Unsupported user role: " + role);
        }

        return conversations.stream()
                .map(conv -> ConversationsResponse.builder()
                        .id(conv.getId())
                        .artistId(conv.getPainting().getArtist().getId())
                        .paintingUrl(fileUrlService.buildUrl(conv.getPainting().getFirstImageFileKey()))
                        .paintingTitle(conv.getPainting().getTitle())
                        .otherUserNickname(role == UserRole.HOTEL ?
                                conv.getPainting().getArtist().getNickname() :
                                conv.getHotel().getNickname())
                        .build())
                .toList();
    }

    public List<MessageResponse> getMessages(long conversationId, long userId) {
        log.debug("Fetching messages for conversation ID: {} by user ID: {}", conversationId, userId);

        Conversation conv = conversationRepository.getOrThrow(conversationId);
        conv.validateParticipantOrThrow(userId);

        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId).stream()
                .map(msg -> MessageResponse.builder()
                        .senderId(msg.getSender().getId())
                        .receiverId(msg.getReceiver().getId())
                        .message(msg.getMessageContent())
                        .sentDate(msg.getCreatedAt())
                        .build())
                .toList();
    }
    
}
