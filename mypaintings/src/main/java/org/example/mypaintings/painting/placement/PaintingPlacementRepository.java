package org.example.mypaintings.painting.placement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;


public interface PaintingPlacementRepository extends JpaRepository<PaintingPlacement,Long> {
    Optional<PaintingPlacement> findByPaintingIdAndArchivedFalse(Long paintingId);
    boolean existsByPaintingIdAndArchivedFalse(long paintingId);
    boolean existsByPaintingIdAndArchivedFalseAndPlacementStatusIn(long paintingId, List<PlacementStatus> statuses);

    @Query("SELECT pl FROM PaintingPlacement pl JOIN FETCH pl.painting p LEFT JOIN FETCH p.attachments WHERE pl.hotel.id = :hotelId AND pl.placementStatus IN :statuses")
    List<PaintingPlacement> findByHotelIdAndPlacementStatusInWithPaintingAndAttachments(@Param("hotelId") Long hotelId, @Param("statuses") Collection<PlacementStatus> statuses);

}
