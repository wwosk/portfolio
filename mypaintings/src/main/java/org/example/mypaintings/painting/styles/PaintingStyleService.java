package org.example.mypaintings.painting.styles;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.mypaintings.exception.notfound.ResourceNotFoundException;
import org.example.mypaintings.painting.Painting;
import org.example.mypaintings.painting.PaintingQueryService;
import org.example.mypaintings.painting.event.PaintingCreatedEvent;
import org.example.mypaintings.painting.styles.dto.StyleResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaintingStyleService {

    private final PaintingStyleRepository styleRepository;
    private final PaintingQueryService paintingQueryService;

    @Value("${app.officialThreshold:5}")
    private long officialThreshold;

    @Transactional
    public StyleResponse getOrCreateStyle(String styleName) {
        String normalized = styleName != null ? styleName.trim().toLowerCase() : "";
        PaintingStyle style = styleRepository.findByNormalizedName(normalized)
                .orElseGet(() -> {
                    try {
                        return styleRepository.save(
                                new PaintingStyle(styleName)
                        );
                    } catch (DataIntegrityViolationException e) {
                        return styleRepository.findByNormalizedName(normalized)
                                .orElseThrow(() -> e);
                    }
                });

        return StyleResponse.fromEntity(style);
    }


    @Transactional(readOnly = true)
    public List<StyleResponse> autocomplete(String query, int limit) {
        return styleRepository.autocomplete(query, PageRequest.of(0, limit))
                .stream()
                .map(StyleResponse::fromEntity)
                .collect(Collectors.toList());
    }


    @Transactional(readOnly = true)
    public List<StyleResponse> getPopularOfficialStyles(int limit) {
        return styleRepository.findByIsOfficialTrueOrderByUsageCountDesc(PageRequest.of(0, limit))
                .stream()
                .map(StyleResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PaintingStyle> resolveStyles(List<Long> styleIds) {
        if (styleIds == null || styleIds.isEmpty()) {
            return List.of();
        }

        List<PaintingStyle> styles = styleRepository.findAllById(styleIds);

        if (styles.size() != styleIds.size()) {
            log.warn("Not all specified styles were found. Expected: {}, found: {}",
                    styleIds.size(), styles.size());
            throw new ResourceNotFoundException(PaintingStyle.class,
                    "Some of the styles were not found ");
        }

        return styles;
    }


    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePaintingCreated(PaintingCreatedEvent event) {
        log.info("Handling PaintingCreatedEvent for painting ID: {}", event.paintingId());
        Painting painting = paintingQueryService.getActiveWithDetailsOrThrow(event.paintingId());
        for (PaintingStyle style : painting.getStyles()) style.incrementUsageCount(officialThreshold);
        log.info("Successfully updated usage count for styles of painting ID: {}", event.paintingId());
    }

}