package org.example.mypaintings.painting.placement;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.mypaintings.exception.access.UnauthorizedAccessException;
import org.example.mypaintings.exception.painting.IllegalStateTransitionException;
import org.example.mypaintings.painting.Painting;
import org.example.mypaintings.user.User;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;



@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaintingPlacement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(nullable = false, updatable = false)
    private Painting painting;

    @ManyToOne(optional = false)
    @JoinColumn(nullable = false, updatable = false)
    private User hotel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlacementStatus placementStatus;

    @Column(length = 1000)
    @Setter
    private String note;

    @Column(nullable = false)
    private boolean archived;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant placementDate;

    public void transitionTo(PlacementStatus nextStatus) {
        if (!this.placementStatus.canTransitionTo(nextStatus)) {
            throw new IllegalStateTransitionException(PlacementStatus.class, this, nextStatus);
        }
        this.placementStatus = nextStatus;
        this.archived = nextStatus.isTerminal();
    }


    public static PaintingPlacement startCycle(Painting painting, User hotel) {
        PaintingPlacement placement = new PaintingPlacement();
        placement.painting = painting;
        placement.hotel = hotel;
        placement.placementStatus = PlacementStatus.IN_DELIVERY_TO_HOTEL;
        placement.note = "";
        return placement;
    }

    public void verifyHotelAccess(long hotelId) {
        if (!this.hotel.getId().equals(hotelId)) {
            throw new UnauthorizedAccessException(
                    "Hotel is not in possession of the painting it tries to manage.");
        }
    }

}