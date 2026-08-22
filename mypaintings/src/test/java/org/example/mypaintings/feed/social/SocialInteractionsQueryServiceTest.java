package org.example.mypaintings.feed.social;

import org.example.mypaintings.feed.social.dto.PaintingLikeCountDto;
import org.example.mypaintings.painting.Painting;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SocialInteractionsQueryService Test Suite")
class SocialInteractionsQueryServiceTest {

    @Mock
    private PaintingLikeRepository paintingLikeRepository;

    @InjectMocks
    private SocialInteractionsQueryService service;

    // ------------------------------------------------------------------
    // isPaintingLikedByHotel
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("isPaintingLikedByHotel")
    class IsPaintingLikedByHotel {

        @Test
        @DisplayName("delegates check to repository correctly")
        void isPaintingLikedByHotel_delegatesToRepository() {
            // given
            Painting painting = mock(Painting.class);
            when(paintingLikeRepository.existsByPaintingAndHotelId(painting, 5L)).thenReturn(true);

            // when
            boolean result = service.isPaintingLikedByHotel(painting, 5L);

            // then
            assertThat(result).isTrue();
            verify(paintingLikeRepository).existsByPaintingAndHotelId(painting, 5L);
        }
    }

    // ------------------------------------------------------------------
    // findLikedPaintingIds
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("findLikedPaintingIds")
    class FindLikedPaintingIds {

        @Test
        @DisplayName("delegates to repository when painting IDs are provided")
        void findLikedPaintingIds_delegatesToRepository_whenIdsProvided() {
            // given
            List<Long> paintingIds = List.of(1L, 2L, 3L);
            when(paintingLikeRepository.findLikedPaintingIdsByHotelAndPaintingIds(5L, paintingIds))
                    .thenReturn(Set.of(1L, 3L));

            // when
            Set<Long> result = service.findLikedPaintingIds(5L, paintingIds);

            // then
            assertThat(result).containsExactlyInAnyOrder(1L, 3L);
            verify(paintingLikeRepository).findLikedPaintingIdsByHotelAndPaintingIds(5L, paintingIds);
        }

        @Test
        @DisplayName("returns empty set without calling repository when IDs collection is null")
        void findLikedPaintingIds_returnsEmptySet_withoutCallingRepository_whenIdsIsNull() {
            // when
            Set<Long> result = service.findLikedPaintingIds(5L, null);

            // then
            assertThat(result).isEmpty();
            verifyNoInteractions(paintingLikeRepository);
        }

        @Test
        @DisplayName("returns empty set without calling repository when IDs collection is empty")
        void findLikedPaintingIds_returnsEmptySet_withoutCallingRepository_whenIdsIsEmpty() {
            // when
            Set<Long> result = service.findLikedPaintingIds(5L, List.of());

            // then
            assertThat(result).isEmpty();
            verifyNoInteractions(paintingLikeRepository);
        }
    }

    // ------------------------------------------------------------------
    // getLikesCountForPaintings
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("getLikesCountForPaintings")
    class GetLikesCountForPaintings {

        @Test
        @DisplayName("maps DTO list from repository to count map correctly")
        void getLikesCountForPaintings_mapsDtoListToMap() {
            // given
            List<Long> paintingIds = List.of(1L, 2L);
            when(paintingLikeRepository.countLikesForPaintingIds(paintingIds))
                    .thenReturn(List.of(
                            new PaintingLikeCountDto(1L, 10L),
                            new PaintingLikeCountDto(2L, 3L)
                    ));

            // when
            Map<Long, Long> result = service.getLikesCountForPaintings(paintingIds);

            // then
            assertThat(result)
                    .hasSize(2)
                    .containsEntry(1L, 10L)
                    .containsEntry(2L, 3L);
            verify(paintingLikeRepository).countLikesForPaintingIds(paintingIds);
        }

        @Test
        @DisplayName("returns empty map without calling repository when IDs list is null")
        void getLikesCountForPaintings_returnsEmptyMap_withoutCallingRepository_whenIdsIsNull() {
            // when
            Map<Long, Long> result = service.getLikesCountForPaintings(null);

            // then
            assertThat(result).isEmpty();
            verifyNoInteractions(paintingLikeRepository);
        }

        @Test
        @DisplayName("returns empty map without calling repository when IDs list is empty")
        void getLikesCountForPaintings_returnsEmptyMap_withoutCallingRepository_whenIdsIsEmpty() {
            // when
            Map<Long, Long> result = service.getLikesCountForPaintings(List.of());

            // then
            assertThat(result).isEmpty();
            verifyNoInteractions(paintingLikeRepository);
        }

        @Test
        @DisplayName("returns empty map when repository returns empty list")
        void getLikesCountForPaintings_returnsEmptyMap_whenRepositoryReturnsEmpty() {
            // given
            List<Long> paintingIds = List.of(1L, 2L);
            when(paintingLikeRepository.countLikesForPaintingIds(paintingIds)).thenReturn(List.of());

            // when
            Map<Long, Long> result = service.getLikesCountForPaintings(paintingIds);

            // then
            assertThat(result).isEmpty();
        }
    }
}
