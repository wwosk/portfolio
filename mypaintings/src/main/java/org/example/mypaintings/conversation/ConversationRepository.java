package org.example.mypaintings.conversation;

import org.example.mypaintings.painting.Painting;
import org.example.mypaintings.user.User;
import org.example.mypaintings.exception.notfound.ResourceNotFoundException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;



public interface ConversationRepository extends JpaRepository<Conversation, Long> {
        Optional<Conversation> findByPaintingAndHotel(Painting painting, User hotel);
        default Conversation getOrThrow(Long id) {
                return findById(id).orElseThrow(() ->
                     new ResourceNotFoundException(Conversation.class, id));
        }



    List<Conversation> findAllByPainting(Painting painting);


    @Query("SELECT c FROM Conversation c " +
            "JOIN FETCH c.painting p " +
            "LEFT JOIN FETCH p.attachments " +
            "JOIN FETCH p.artist a " +
            "JOIN FETCH c.hotel h " +
            "WHERE c.hotel.id = :hotelId " +
            "AND (c.archived IS NULL OR c.archived NOT IN ('HOTEL', 'BOTH'))")
    List<Conversation> findActiveByHotelIdWithDetails(@Param("hotelId") long hotelId);

    @Query("SELECT c FROM Conversation c " +
            "JOIN FETCH c.painting p " +
            "LEFT JOIN FETCH p.attachments " +
            "JOIN FETCH p.artist a " +
            "JOIN FETCH c.hotel h " +
            "WHERE p.artist.id = :artistId " +
            "AND (c.archived IS NULL OR c.archived NOT IN ('ARTIST', 'BOTH'))")
    List<Conversation> findActiveByPaintingArtistIdWithDetails(@Param("artistId") long artistId);
}
