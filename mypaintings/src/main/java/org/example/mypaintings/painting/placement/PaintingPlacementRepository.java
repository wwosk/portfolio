package org.example.mypaintings.painting.placement;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;


public interface PaintingPlacementRepository extends JpaRepository<PaintingPlacement,Long> {
    Optional<PaintingPlacement> findByPaintingIdAndArchivedFalse(Long paintingId);
    boolean existsByPaintingIdAndArchivedFalse(long paintingId);
}
