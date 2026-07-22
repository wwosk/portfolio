package org.example.mypaintings.feed.social;

import org.example.mypaintings.feed.social.dto.PaintingLikeCountDto;
import org.example.mypaintings.painting.Painting;
import org.example.mypaintings.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface PaintingLikeRepository extends JpaRepository<PaintingLike, Long> {
    Optional<PaintingLike> findByPaintingAndHotel(Painting painting, User hotel);


    boolean existsByPaintingAndHotelId(Painting painting, long hotelId);

    @Query("SELECT l.painting.id FROM PaintingLike l WHERE l.hotel.id = :hotelId AND l.painting.id IN :paintingIds")
    Set<Long> findLikedPaintingIdsByHotelAndPaintingIds(@Param("hotelId") long hotelId, @Param("paintingIds") Collection<Long> paintingIds);

    @Query("SELECT NEW org.example.mypaintings.feed.social.dto.PaintingLikeCountDto(l.painting.id, COUNT(l)) " +
            "FROM PaintingLike l WHERE l.painting.id IN :paintingIds GROUP BY l.painting.id")
    List<PaintingLikeCountDto> countLikesForPaintingIds(@Param("paintingIds") List<Long> paintingIds);

}
