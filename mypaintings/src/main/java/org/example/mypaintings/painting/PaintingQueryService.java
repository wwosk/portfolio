package org.example.mypaintings.painting;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.mypaintings.exception.notfound.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;



@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class PaintingQueryService {
    private final PaintingRepository paintingRepository;

    @Transactional(readOnly = true)
    public Painting getPaintingWithLockOrThrow(long paintingId) {
        return paintingRepository.findByIdWithLock(paintingId)
                .orElseThrow(() -> new ResourceNotFoundException(Painting.class, paintingId));
    }

    @Transactional(readOnly = true)
    public Painting getActiveWithDetailsOrThrow(long paintingId) {
        Painting painting = paintingRepository.getWithDetailsOrThrow(paintingId);
        ensureNotArchived(painting);
        return painting;
    }

    private void ensureNotArchived(Painting painting) {
        if (painting.isArchived()) {
            log.warn("Attempt to process archived painting ID: {}", painting.getId());
            throw new IllegalStateException("Painting is archived and cannot be processed!");
        }
    }


}
