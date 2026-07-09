package org.example.mypaintings.painting.placement;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.mypaintings.exception.notfound.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class PaintingPlacementQueryService {

    private final PaintingPlacementRepository paintingPlacementRepository;


    public PaintingPlacement getActivePlacement(long paintingId) {
        return paintingPlacementRepository.findByPaintingIdAndArchivedFalse(paintingId)
                .orElseThrow(() -> {
                    log.error("Active placement cycle not found for painting ID: {}", paintingId);
                    return new ResourceNotFoundException(PaintingPlacement.class, "PaintingId" + paintingId);
                });
    }

    public boolean isSold(long paintingId) {
        List<PlacementStatus> soldStatuses = Arrays.stream(PlacementStatus.values())
                .filter(PlacementStatus::isSoldStatus)
                .toList();

        return paintingPlacementRepository.existsByPaintingIdAndArchivedFalseAndPlacementStatusIn(paintingId, soldStatuses);
    }

    public boolean isCurrentlyPlaced(long paintingId) {
        return paintingPlacementRepository.existsByPaintingIdAndArchivedFalse(paintingId);
    }


}
