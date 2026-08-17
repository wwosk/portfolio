package org.example.mypaintings.sale;

import org.example.mypaintings.painting.Painting;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SaleQueryServiceTest {

    @Mock
    private SaleRepository saleRepository;

    @InjectMocks
    private SaleQueryService saleQueryService;

    private static final long ARTIST_ID = 7L;

    // ------------------------------------------------------------------
    // getSalesByArtistId
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("getSalesByArtistId")
    class GetSalesByArtistId {

        @Test
        @DisplayName("delegates to repository and returns result")
        void delegatesToRepository() {
            List<Sale> expected = List.of(mock(Sale.class), mock(Sale.class));
            when(saleRepository.findAllByPaintingArtistIdWithPaintingAndAttachments(ARTIST_ID))
                    .thenReturn(expected);

            List<Sale> result = saleQueryService.getSalesByArtistId(ARTIST_ID);

            assertThat(result).containsExactlyElementsOf(expected);
            verify(saleRepository)
                    .findAllByPaintingArtistIdWithPaintingAndAttachments(ARTIST_ID);
        }

        @Test
        @DisplayName("returns empty list when artist has no sales")
        void returnsEmptyList() {
            when(saleRepository.findAllByPaintingArtistIdWithPaintingAndAttachments(ARTIST_ID))
                    .thenReturn(Collections.emptyList());

            List<Sale> result = saleQueryService.getSalesByArtistId(ARTIST_ID);

            assertThat(result).isEmpty();
        }
    }

    // ------------------------------------------------------------------
    // getMappedSalesForPaintings
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("getMappedSalesForPaintings")
    class GetMappedSalesForPaintings {

        @Test
        @DisplayName("returns empty map and skips repository when input is null")
        void returnsEmptyMapWhenInputIsNull() {
            Map<Long, Sale> result = saleQueryService.getMappedSalesForPaintings(null);

            assertThat(result).isEmpty();
            verifyNoInteractions(saleRepository);
        }

        @Test
        @DisplayName("returns empty map and skips repository when input is empty")
        void returnsEmptyMapWhenInputIsEmpty() {
            Map<Long, Sale> result = saleQueryService
                    .getMappedSalesForPaintings(Collections.emptyList());

            assertThat(result).isEmpty();
            verifyNoInteractions(saleRepository);
        }

        @Test
        @DisplayName("maps each sale by its painting id")
        void mapsSalesByPaintingId() {
            List<Long> ids = List.of(1L, 2L, 3L);
            Sale sale1 = saleForPainting(1L);
            Sale sale2 = saleForPainting(3L);

            when(saleRepository.findAllByPaintingIdInWithDetails(ids))
                    .thenReturn(List.of(sale1, sale2));

            Map<Long, Sale> result = saleQueryService.getMappedSalesForPaintings(ids);

            assertThat(result)
                    .containsOnlyKeys(1L, 3L)
                    .containsEntry(1L, sale1)
                    .containsEntry(3L, sale2);
            verify(saleRepository).findAllByPaintingIdInWithDetails(ids);
        }

        @Test
        @DisplayName("returns empty map when repository returns no sales")
        void returnsEmptyMapWhenNoSales() {
            List<Long> ids = List.of(1L, 2L);
            when(saleRepository.findAllByPaintingIdInWithDetails(ids))
                    .thenReturn(Collections.emptyList());

            Map<Long, Sale> result = saleQueryService.getMappedSalesForPaintings(ids);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("keys are taken from painting id, not sale id")
        void keysArePaintingIds() {
            List<Long> ids = List.of(100L);
            Sale sale = saleForPainting(100L);

            when(saleRepository.findAllByPaintingIdInWithDetails(ids))
                    .thenReturn(List.of(sale));

            Map<Long, Sale> result = saleQueryService.getMappedSalesForPaintings(ids);

            assertThat(result).containsOnlyKeys(100L);
            // Kontrakt: serwis czyta painting.getId(), a nie sale.getId().
            verify(sale, never()).getId();
        }

        @Test
        @DisplayName("throws IllegalStateException on duplicate painting ids (toMap has no merge function)")
        void throwsOnDuplicatePaintingIds() {
            List<Long> ids = List.of(1L);
            Sale sale1 = saleForPainting(1L);
            Sale sale2 = saleForPainting(1L);

            when(saleRepository.findAllByPaintingIdInWithDetails(ids))
                    .thenReturn(List.of(sale1, sale2));

            assertThatThrownBy(() -> saleQueryService.getMappedSalesForPaintings(ids))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Duplicate key");
        }

        @Test
        @DisplayName("throws NullPointerException when sale has no painting")
        void throwsNpeWhenSaleHasNoPainting() {
            List<Long> ids = List.of(1L);
            Sale sale = mock(Sale.class);
            when(sale.getPainting()).thenReturn(null);

            when(saleRepository.findAllByPaintingIdInWithDetails(ids))
                    .thenReturn(List.of(sale));

            assertThatThrownBy(() -> saleQueryService.getMappedSalesForPaintings(ids))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * Tworzy mocka {@link Sale} powiązanego z obrazem o podanym id.
     * Używamy {@code lenient()}, bo w testach, które nie dotykają
     * {@code sale.getPainting().getId()} (np. testy pustej listy), stub byłby nieużywany.
     */
    private Sale saleForPainting(long paintingId) {
        Painting painting = mock(Painting.class);
        lenient().when(painting.getId()).thenReturn(paintingId);

        Sale sale = mock(Sale.class);
        lenient().when(sale.getPainting()).thenReturn(painting);
        return sale;
    }
}