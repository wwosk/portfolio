package org.example.mypaintings.geolocation;

import jakarta.persistence.*;
import lombok.*;
import org.example.mypaintings.user.User;
import java.time.Instant;

@Getter
@Setter
@Entity
@Table(
        name = "user_proximity",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "nearby_user_id"}),
        indexes = {
        @Index(
                name = "idx_user_dist_nearby",
                columnList = "user_id, distanceKm, nearby_user_id"
        )
})
@NoArgsConstructor
public class UserProximity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne
    @JoinColumn(name = "nearby_user_id", nullable = false)
    private User nearbyUser;

    @Column(nullable = false)
    private double distanceKm;

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    @Version
    private Long version;


    public UserProximity(User user, User nearbyUser, double distanceKm) {
        this.user = user;
        this.nearbyUser = nearbyUser;
        this.distanceKm = distanceKm;
    }

}
