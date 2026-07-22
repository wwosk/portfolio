package org.example.mypaintings.feed.social;


import lombok.RequiredArgsConstructor;
import org.example.mypaintings.feed.social.dto.PaintingLikeCountDto;
import org.example.mypaintings.painting.Painting;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class SocialInteractionsQueryService {

    private final PaintingLikeRepository paintingLikeRepository;


    public boolean isPaintingLikedByHotel(Painting painting, long hotelId) {
        return paintingLikeRepository.existsByPaintingAndHotelId(painting, hotelId);
    }

    public Set<Long> findLikedPaintingIds(long hotelId, Collection<Long> paintingIds) {
        if (paintingIds == null || paintingIds.isEmpty()) {
            return Set.of();
        }
        return paintingLikeRepository.findLikedPaintingIdsByHotelAndPaintingIds(hotelId, paintingIds);
    }

    public Map<Long, Long> getLikesCountForPaintings(List<Long> paintingIds) {
        if (paintingIds == null || paintingIds.isEmpty()) {
            return Map.of();
        }
        return paintingLikeRepository.countLikesForPaintingIds(paintingIds).stream()
                .collect(Collectors.toMap(
                        PaintingLikeCountDto::paintingId,
                        PaintingLikeCountDto::likesCount
                ));
    }


}
