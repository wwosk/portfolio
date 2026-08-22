package org.example.mypaintings.painting.placement;

import org.example.mypaintings.exception.notfound.ResourceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaintingPlacementQueryService Test Suite")
class PaintingPlacementQueryServiceTest {

    @Mock
    private PaintingPlacementRepository paintingPlacementRepository;

    @InjectMocks
    private PaintingPlacementQueryService paintingPlacementQueryService;

    private static final long PAINTING_ID = 42L;
    private static final long ARTIST_ID = 7L;
    private static final long HOTEL_ID = 99L;

    // ------------------------------------------------------------------
    // isCurrentlyPlaced
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("isCurrentlyPlaced")
    class IsCurrentlyPlaced {

        @Test
        @DisplayName("returns true when repository reports an active placement")
        void returnsTrueWhenActivePlacementExists() {
            // given
            when(paintingPlacementRepository.existsByPaintingIdAndArchivedFalse(PAINTING_ID))
                    .thenReturn(true);

            // when
            boolean result = paintingPlacementQueryService.isCurrentlyPlaced(PAINTING_ID);

            // then
            assertThat(result).isTrue();
            verify(paintingPlacementRepository).existsByPaintingIdAndArchivedFalse(PAINTING_ID);
        }

        @Test
        @DisplayName("returns false when repository reports no active placement")
        void returnsFalseWhenNoActivePlacement() {
            // given
            when(paintingPlacementRepository.existsByPaintingIdAndArchivedFalse(PAINTING_ID))
                    .thenReturn(false);

            // when
            boolean result = paintingPlacementQueryService.isCurrentlyPlaced(PAINTING_ID);

            // then
            assertThat(result).isFalse();
            verify(paintingPlacementRepository).existsByPaintingIdAndArchivedFalse(PAINTING_ID);
        }
    }

    // ------------------------------------------------------------------
    // getActivePlacementsForArtist
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("getActivePlacementsForArtist")
    class GetActivePlacementsForArtist {

        @Test
        @DisplayName("delegates to repository with terminal statuses and returns result")
        void delegatesWithTerminalStatuses() {
            // given
            List<PaintingPlacement> expected = List.of(
                    mockPlacement(), mockPlacement()
            );

            when(paintingPlacementRepository
                    .findAllActiveByPaintingArtistIdWithPaintingAndAttachments(eq(ARTIST_ID), anyCollection()))
                    .thenReturn(expected);

            // when
            List<PaintingPlacement> result =
                    paintingPlacementQueryService.getActivePlacementsForArtist(ARTIST_ID);

            // then
            assertThat(result).containsExactlyElementsOf(expected);
            verify(paintingPlacementRepository)
                    .findAllActiveByPaintingArtistIdWithPaintingAndAttachments(eq(ARTIST_ID), anyCollection());
        }

        @Test
        @DisplayName("returns empty list when repository returns empty list")
        void returnsEmptyListWhenRepositoryReturnsEmpty() {
            // given
            when(paintingPlacementRepository
                    .findAllActiveByPaintingArtistIdWithPaintingAndAttachments(eq(ARTIST_ID), anyCollection()))
                    .thenReturn(Collections.emptyList());

            // when
            List<PaintingPlacement> result =
                    paintingPlacementQueryService.getActivePlacementsForArtist(ARTIST_ID);

            // then
            assertThat(result).isEmpty();
        }
    }

    // ------------------------------------------------------------------
    // isSold
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("isSold")
    class IsSold {

        @Test
        @DisplayName("returns true when repository reports a sold status")
        void returnsTrueWhenSold() {
            // given
            when(paintingPlacementRepository
                    .existsByPaintingIdAndArchivedFalseAndPlacementStatusIn(eq(PAINTING_ID), anyList()))
                    .thenReturn(true);

            // when
            boolean result = paintingPlacementQueryService.isSold(PAINTING_ID);

            // then
            assertThat(result).isTrue();
            verify(paintingPlacementRepository)
                    .existsByPaintingIdAndArchivedFalseAndPlacementStatusIn(eq(PAINTING_ID), anyList());
        }

        @Test
        @DisplayName("returns false when repository reports no sold status")
        void returnsFalseWhenNotSold() {
            // given
            when(paintingPlacementRepository
                    .existsByPaintingIdAndArchivedFalseAndPlacementStatusIn(eq(PAINTING_ID), anyList()))
                    .thenReturn(false);

            // when
            boolean result = paintingPlacementQueryService.isSold(PAINTING_ID);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("passes only sold statuses to repository")
        void passesOnlySoldStatuses() {
            // given
            when(paintingPlacementRepository
                    .existsByPaintingIdAndArchivedFalseAndPlacementStatusIn(eq(PAINTING_ID), anyList()))
                    .thenReturn(true);

            // when
            paintingPlacementQueryService.isSold(PAINTING_ID);

            // then
            verify(paintingPlacementRepository).existsByPaintingIdAndArchivedFalseAndPlacementStatusIn(
                    eq(PAINTING_ID),
                    org.mockito.ArgumentMatchers.argThat(statuses ->
                            statuses.stream().allMatch(PlacementStatus::isSoldStatus))
            );
        }
    }

    // ------------------------------------------------------------------
    // getActivePlacement
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("getActivePlacement")
    class GetActivePlacement {

        @Test
        @DisplayName("returns placement when found")
        void returnsPlacementWhenFound() {
            // given
            PaintingPlacement placement = mockPlacement();
            when(paintingPlacementRepository.findByPaintingIdAndArchivedFalse(PAINTING_ID))
                    .thenReturn(Optional.of(placement));

            // when
            PaintingPlacement result = paintingPlacementQueryService.getActivePlacement(PAINTING_ID);

            // then
            assertThat(result).isSameAs(placement);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when not found")
        void throwsWhenNotFound() {
            // given
            when(paintingPlacementRepository.findByPaintingIdAndArchivedFalse(PAINTING_ID))
                    .thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> paintingPlacementQueryService.getActivePlacement(PAINTING_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ------------------------------------------------------------------
    // getPaintingsOnStockForHotel
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("getPaintingsOnStockForHotel")
    class GetPaintingsOnStockForHotel {

        @Test
        @DisplayName("delegates to repository with on-stock statuses")
        void delegatesWithOnStockStatuses() {
            // given
            List<PaintingPlacement> expected = List.of(mockPlacement());
            when(paintingPlacementRepository
                    .findByHotelIdAndPlacementStatusInWithPaintingAndAttachments(eq(HOTEL_ID), anyCollection()))
                    .thenReturn(expected);

            // when
            List<PaintingPlacement> result =
                    paintingPlacementQueryService.getPaintingsOnStockForHotel(HOTEL_ID);

            // then
            assertThat(result).containsExactlyElementsOf(expected);
            verify(paintingPlacementRepository)
                    .findByHotelIdAndPlacementStatusInWithPaintingAndAttachments(eq(HOTEL_ID), anyCollection());
        }

        @Test
        @DisplayName("returns empty list when repository returns empty")
        void returnsEmptyList() {
            // given
            when(paintingPlacementRepository
                    .findByHotelIdAndPlacementStatusInWithPaintingAndAttachments(eq(HOTEL_ID), anyCollection()))
                    .thenReturn(Collections.emptyList());

            // when
            List<PaintingPlacement> result =
                    paintingPlacementQueryService.getPaintingsOnStockForHotel(HOTEL_ID);

            // then
            assertThat(result).isEmpty();
        }
    }

    // ------------------------------------------------------------------
    // getOrdersNeedingActionForHotel
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("getOrdersNeedingActionForHotel")
    class GetOrdersNeedingActionForHotel {

        @Test
        @DisplayName("delegates to repository with action-required statuses")
        void delegatesWithActionRequiredStatuses() {
            // given
            List<PaintingPlacement> expected = List.of(mockPlacement());
            when(paintingPlacementRepository
                    .findByHotelIdAndPlacementStatusInWithPaintingAndAttachments(eq(HOTEL_ID), anyCollection()))
                    .thenReturn(expected);

            // when
            List<PaintingPlacement> result =
                    paintingPlacementQueryService.getOrdersNeedingActionForHotel(HOTEL_ID);

            // then
            assertThat(result).containsExactlyElementsOf(expected);
            verify(paintingPlacementRepository)
                    .findByHotelIdAndPlacementStatusInWithPaintingAndAttachments(eq(HOTEL_ID), anyCollection());
        }

        @Test
        @DisplayName("returns empty list when no orders need action")
        void returnsEmptyListWhenNoOrdersNeedAction() {
            // given
            when(paintingPlacementRepository
                    .findByHotelIdAndPlacementStatusInWithPaintingAndAttachments(eq(HOTEL_ID), anyCollection()))
                    .thenReturn(Collections.emptyList());

            // when
            List<PaintingPlacement> result =
                    paintingPlacementQueryService.getOrdersNeedingActionForHotel(HOTEL_ID);

            // then
            assertThat(result).isEmpty();
        }
    }

    // ------------------------------------------------------------------
    // findPaintingIdsCurrentlyPlaced
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("findPaintingIdsCurrentlyPlaced")
    class FindPaintingIdsCurrentlyPlaced {

        @Test
        @DisplayName("returns empty set and skips repository when input is null")
        void returnsEmptySetWhenInputIsNull() {
            // when
            Set<Long> result = paintingPlacementQueryService.findPaintingIdsCurrentlyPlaced(null);

            // then
            assertThat(result).isEmpty();
            verifyNoInteractions(paintingPlacementRepository);
        }

        @Test
        @DisplayName("returns empty set and skips repository when input is empty")
        void returnsEmptySetWhenInputIsEmpty() {
            // when
            Set<Long> result = paintingPlacementQueryService.findPaintingIdsCurrentlyPlaced(Collections.emptyList());

            // then
            assertThat(result).isEmpty();
            verifyNoInteractions(paintingPlacementRepository);
        }

        @Test
        @DisplayName("delegates to repository when input is non-empty")
        void delegatesWhenInputNonEmpty() {
            // given
            List<Long> ids = Arrays.asList(1L, 2L, 3L);
            Set<Long> expected = Set.of(1L, 3L);

            when(paintingPlacementRepository.findPaintingIdsCurrentlyPlaced(ids))
                    .thenReturn(expected);

            // when
            Set<Long> result = paintingPlacementQueryService.findPaintingIdsCurrentlyPlaced(ids);

            // then
            assertThat(result).isEqualTo(expected);
            verify(paintingPlacementRepository).findPaintingIdsCurrentlyPlaced(ids);
        }

        @Test
        @DisplayName("returns empty set when repository returns empty set")
        void returnsEmptySetWhenRepositoryReturnsEmpty() {
            // given
            List<Long> ids = List.of(1L, 2L);
            when(paintingPlacementRepository.findPaintingIdsCurrentlyPlaced(ids))
                    .thenReturn(Set.of());

            // when
            Set<Long> result = paintingPlacementQueryService.findPaintingIdsCurrentlyPlaced(ids);

            // then
            assertThat(result).isEmpty();
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private PaintingPlacement mockPlacement() {
        return mock(PaintingPlacement.class);
    }
}
