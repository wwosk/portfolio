package org.example.mypaintings.painting.styles.dto;

import org.example.mypaintings.painting.styles.PaintingStyle;


public record StyleResponse(
        Long id,
        String name,
        boolean isOfficial,
        long usageCount
) {
    public static StyleResponse fromEntity(PaintingStyle entity) {
        if (entity == null) {
            return null;
        }
        return new StyleResponse(
                entity.getId(),
                entity.getName(),
                entity.isOfficial(),
                entity.getUsageCount()
        );
    }
}