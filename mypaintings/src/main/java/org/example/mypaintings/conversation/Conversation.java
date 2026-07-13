package org.example.mypaintings.conversation;

import jakarta.persistence.*;
import lombok.*;
import org.example.mypaintings.exception.access.UnauthorizedAccessException;
import org.example.mypaintings.exception.messages.SelfMessageException;
import org.example.mypaintings.painting.Painting;
import org.example.mypaintings.reservation.ReservationOffer;
import org.example.mypaintings.user.User;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;



@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "reservation_conversation",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_painting_hotel",
                        columnNames = {"painting_id", "hotel_id"}
                )
        }
)
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "painting_id", nullable = false)
    private Painting painting;

    @ManyToOne
    @JoinColumn(name = "hotel_id", nullable = false)
    private User hotel;

    @OneToOne
    @JoinColumn(name = "offer_id", nullable = false)
    private ReservationOffer offer;

    @Enumerated(EnumType.STRING)
    @Column(name = "archived_status", length = 20)
    private ArchivedBy archived;


    @OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Message> messages = new ArrayList<>();



    public Message createMessage(User sender, User receiver, String content) {

        if (!isParticipant(sender.getId()) || !isParticipant(receiver.getId())) {
            throw new UnauthorizedAccessException("User is not a participant");
        }

        if (sender.getId().equals(receiver.getId()))
            throw new SelfMessageException("Cannot send message to self");

        Message newMessage = Message.builder()
                .conversation(this)
                .sender(sender)
                .receiver(receiver)
                .messageContent(content)
                .createdAt(Instant.now())
                .build();

        this.messages.add(newMessage);
        return newMessage;
    }

    public boolean isParticipant(Long userId) {
        return hotel.getId().equals(userId) || painting.getArtist().getId().equals(userId);
    }

    public void validateParticipantOrThrow(long userId) {
        if (!isParticipant(userId)) {
            throw new AccessDeniedException("User is not a participant of this conversation");
        }
    }


    public void archiveFor(long userId) {
        validateParticipantOrThrow(userId);
        boolean isHotel = hotel.getId().equals(userId);
        if (archived == null) {
            this.archived = isHotel ? ArchivedBy.HOTEL : ArchivedBy.ARTIST;
        } else if (isHotel && archived == ArchivedBy.ARTIST) {
            this.archived = ArchivedBy.BOTH;
        } else if (!isHotel && archived == ArchivedBy.HOTEL) {
            this.archived = ArchivedBy.BOTH;
        }
    }


    @Builder
    public Conversation(Painting painting,
                        User hotel,
                        ReservationOffer offer,
                        ArchivedBy archived,
                        List<Message> messages) {
        this.painting = painting;
        this.hotel = hotel;
        this.offer = offer;
        this.archived = archived;
        this.messages = (messages != null) ? messages : new ArrayList<>();
    }

}