package org.example.mypaintings.reservation;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.mypaintings.exception.painting.IllegalStateTransitionException;
import org.example.mypaintings.painting.Painting;
import org.example.mypaintings.user.User;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

import static org.example.mypaintings.reservation.ReservationStatus.*;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"hotel_id", "painting_id"}))
public class ReservationOffer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "painting_id", nullable = false)
    private Painting painting;

    @ManyToOne
    @JoinColumn(name = "hotel_id", nullable = false)
    private User hotel;

    @Enumerated(EnumType.STRING)
    private ReservationStatus status;


    @CreationTimestamp
    private Instant offerDate = Instant.now();


    public void accept() {
        ensureCanTransitionTo(ACCEPTED);
        this.status = ACCEPTED;
    }
    
    public void reject() {
        ensureCanTransitionTo(REJECTED);
        this.status = REJECTED;
    }

    public void revoke() {
        ensureCanTransitionTo(REVOKED);
        this.status = REVOKED;
    }

    public void cancel() {
        ensureCanTransitionTo(CANCELLED);
        this.status = CANCELLED;
    }

    public void archive(){
        ensureCanTransitionTo(ARCHIVED);
        this.status = ARCHIVED;
    }

    public void retryCreatingOffer() {
        ensureCanTransitionTo(PENDING);
        this.status = PENDING;
    }

    private void ensureCanTransitionTo(ReservationStatus nextStatus) {
        if (this.status == null) {
            throw new IllegalStateException("Reservation offer has no status");
        }

        if (!this.status.canTransitionTo(nextStatus)) {
            throw new IllegalStateTransitionException(ReservationStatus.class,this.status,nextStatus);
        }

    }

    @Builder
    public ReservationOffer(Painting painting,
                            User hotel,
                            ReservationStatus status,
                            Instant offerDate) {
        this.painting = painting;
        this.hotel = hotel;
        this.status = (status != null) ? status : PENDING;
        this.offerDate = (offerDate != null) ? offerDate : Instant.now();
    }

}