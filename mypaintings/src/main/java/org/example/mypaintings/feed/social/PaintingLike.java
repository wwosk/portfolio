package org.example.mypaintings.feed.social;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.mypaintings.painting.Painting;
import org.example.mypaintings.user.User;


@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "painting_likes", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"hotel_id", "painting_id"})
})
public class PaintingLike {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "hotel_id")
    private User hotel;

    @ManyToOne
    @JoinColumn(name = "painting_id")
    private Painting painting;


    public PaintingLike(User hotel, Painting painting) {
        this.hotel = hotel;
        this.painting = painting;
    }

}
