package org.example.mypaintings.painting.placement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;


public interface PaintingPlacementRepository extends JpaRepository<PaintingPlacement,Long> {
    Optional<PaintingPlacement> findByPaintingIdAndArchivedFalse(Long paintingId);
    boolean existsByPaintingIdAndArchivedFalse(long paintingId);
    boolean existsByPaintingIdAndArchivedFalseAndPlacementStatusIn(long paintingId, List<PlacementStatus> statuses);

    @Query("SELECT pl FROM PaintingPlacement pl JOIN FETCH pl.painting p LEFT JOIN FETCH p.attachments WHERE pl.hotel.id = :hotelId AND pl.placementStatus IN :statuses")
    List<PaintingPlacement> findByHotelIdAndPlacementStatusInWithPaintingAndAttachments(@Param("hotelId") Long hotelId, @Param("statuses") Collection<PlacementStatus> statuses);

    @Query("""
    SELECT DISTINCT pl FROM PaintingPlacement pl
    JOIN FETCH pl.painting p
    LEFT JOIN FETCH p.attachments
    WHERE p.artist.id = :artistId
      AND pl.archived = false
      AND pl.placementStatus NOT IN :terminalStatuses
""")
    List<PaintingPlacement> findAllActiveByPaintingArtistIdWithPaintingAndAttachments
            (@Param("artistId") long artistId, @Param("terminalStatuses") Collection<PlacementStatus> terminalStatuses);

    @Query("SELECT p.painting.id FROM PaintingPlacement p WHERE p.painting.id IN :paintingIds AND p.archived = false")
    Set<Long> findPaintingIdsCurrentlyPlaced(@Param("paintingIds") List<Long> paintingIds);

}
