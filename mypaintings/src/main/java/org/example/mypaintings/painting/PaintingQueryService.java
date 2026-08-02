package org.example.mypaintings.painting;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.mypaintings.exception.notfound.ResourceNotFoundException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;


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

    public Painting getOrThrow(long paintingId) {
        return paintingRepository.getOrThrow(paintingId);
    }

    @Transactional(readOnly = true)
    public Painting getActiveOrThrow(long paintingId) {
        Painting painting = paintingRepository.getOrThrow(paintingId);
        ensureNotArchived(painting);
        return painting;
    }


    @Transactional(readOnly = true)
    public long getArtistPaintingCount(long artistId) {
        return paintingRepository.countByArtistId(artistId);
    }

    @Transactional(readOnly = true)
    public List<Painting> getAllArtistPaintings(long artistId) {
        return paintingRepository.findAllByArtistIdWithAttachments(artistId);
    }

    @Transactional(readOnly = true)
    public Slice<Painting> getPaintingSlicesBySpecification(Specification<Painting> spec, Pageable pageable) {
        return paintingRepository.findAll(spec, pageable);
    }

    @Transactional(readOnly = true)
    public List<Painting> getPaintingsWithDetailsByIds(List<Long> paintingIds) {
        return paintingRepository.findAllByIdsWithDetails(paintingIds);
    }

}
