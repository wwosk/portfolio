package org.example.mypaintings.conversation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.example.mypaintings.conversation.dto.MessageResponse;
import org.example.mypaintings.conversation.streaming.ChatStreamService;
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
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;



@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationService {
    private final UserQueryService userQueryService;
    private final ConversationRepository conversationRepository;
    private final ChatStreamService chatStreamService;
    private final PaintingQueryService paintingQueryService;
    private final ReservationQueryService reservationQueryService;
    private final MessageRepository messageRepository;

    @Transactional
    @EventListener
    public void handlePaintingDeleted(PaintingArchivedEvent event) {
        Painting painting = paintingQueryService.getOrThrow(event.paintingId());
        archiveConversationsForPainting(painting);
    }

    @Transactional
    @EventListener
    public void handlePaintingSold(PaintingSoldEvent event) {
        Painting painting = paintingQueryService.getOrThrow(event.paintingId());
        archiveConversationsForPainting(painting);
    }

    @Transactional
    @EventListener
    public void handleReservationRevoked(ReservationRevokedEvent event) {
        Painting painting = paintingQueryService.getOrThrow(event.paintingId());
        archiveConversationsForPainting(painting);
    }

    @Transactional
    @EventListener
    public void handlePlacementStarted(PlacementStartedEvent event) {
        ReservationOffer acceptedOffer = reservationQueryService.getValidOrThrow(event.acceptedOfferId());
        createConversation(event.hotelId(), event.paintingId(), acceptedOffer);
    }

    @Transactional
    public void createConversation(long hotelId, long paintingId, ReservationOffer offer) {
        log.info("Creating or searching for a conversation for hotel ID: {} and painting ID: {}", hotelId, paintingId);

        Painting painting = paintingQueryService.getActiveOrThrow(paintingId);
        User hotel = userQueryService.getValidHotelOrThrow(hotelId);

        conversationRepository.findByPaintingAndHotel(painting, hotel)
                .ifPresentOrElse(
                        conv -> log.info("Found existing conversation with ID: {}", conv.getId()),
                        () -> {
                            Conversation newConversation = Conversation.builder()
                                    .painting(painting)
                                    .hotel(hotel)
                                    .offer(offer)
                                    .archived(null)
                                    .build();
                            Long savedId = conversationRepository.save(newConversation).getId();
                            log.info("Created new conversation with ID: {}", savedId);
                        }
                );
    }

    @Transactional
    public void archiveConversation(long conversationId, long userId) {
        log.info("User ID: {} is trying to archive conversation ID: {}", userId, conversationId);

        Conversation conv = conversationRepository.getOrThrow(conversationId);
        conv.validateParticipantOrThrow(userId);
        conv.archiveFor(userId);

        log.info("Successfully archived conversation ID: {} for user ID: {}", conversationId, userId);
    }

    @Transactional
    public void sendMessage(long senderId, String content, long conversationId) {
        log.debug("Sending message in conversation ID: {} by user ID: {}", conversationId, senderId);

        Conversation conv = conversationRepository.getOrThrow(conversationId);
        long receiverId = (conv.getHotel().getId().equals(senderId))
                ? conv.getPainting().getArtist().getId()
                : conv.getHotel().getId();

        internalCreateMessage(senderId, receiverId, content, conv);
    }

    @Transactional
    public void sendDeliveryInfo(long hotelId, long conversationId) {
        log.info("Generating automated delivery details for hotel ID: {} in conversation ID: {}", hotelId, conversationId);

        Conversation conversation = conversationRepository.getOrThrow(conversationId);
        User hotel = userQueryService.getValidHotelOrThrow(hotelId);
        String automatedMessage = String.format(
                "Automatically generated message, here are my delivery details: %s",
                hotel.getFullAddress()
        );

        long artistId = conversation.getPainting().getArtist().getId();

        internalCreateMessage(hotelId, artistId, automatedMessage, conversation);
    }

    private void internalCreateMessage(long senderId, long receiverId, String content, Conversation conversation) {
        User sender = userQueryService.getUserReference(senderId);
        User receiver = userQueryService.getUserReference(receiverId);
        Message savedMsg = conversation.createMessage(sender, receiver, content);
        messageRepository.save(savedMsg);

        MessageResponse messageDTO = MessageResponse.builder()
                .senderId(savedMsg.getSender().getId())
                .receiverId(savedMsg.getReceiver().getId())
                .message(savedMsg.getMessageContent())
                .sentDate(savedMsg.getCreatedAt())
                .build();

        chatStreamService.notifyNewMessage(conversation.getId(), messageDTO);
        log.debug("Message with ID {} was successfully sent and passed to the stream for conversation ID: {}",
                savedMsg.getId(), conversation.getId());
    }

    @Transactional
    public void archiveConversationsForPainting(Painting painting) {
        List<Conversation> conversations = conversationRepository.findAllByPainting(painting);
        for (Conversation conversation : conversations) {
            conversation.setArchived(ArchivedBy.BOTH);
        }
        conversationRepository.saveAll(conversations);
        log.info("Archived all conversations for painting ID: {}", painting.getId());
    }


}