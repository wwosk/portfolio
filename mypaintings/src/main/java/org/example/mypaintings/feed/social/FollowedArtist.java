package org.example.mypaintings.feed.social;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.example.mypaintings.user.User;

@Entity
@Getter
@Setter
@Table(name = "followed_artists", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"hotel_id", "artist_id"})
})
public class FollowedArtist {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "hotel_id")
    User hotel;

    @ManyToOne
    @JoinColumn(name = "artist_id")
    User artist;

}


