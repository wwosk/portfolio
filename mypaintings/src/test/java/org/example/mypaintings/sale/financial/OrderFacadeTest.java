package org.example.mypaintings.sale.financial;

import org.example.mypaintings.painting.Painting;
import org.example.mypaintings.painting.features.Dimensions;
import org.example.mypaintings.painting.features.Unit;
import org.example.mypaintings.painting.placement.PaintingPlacement;
import org.example.mypaintings.painting.placement.PaintingPlacementQueryService;
import org.example.mypaintings.painting.placement.PlacementStatus;
import org.example.mypaintings.sale.BuyerContactInfo;
import org.example.mypaintings.sale.Sale;
import org.example.mypaintings.sale.SaleQueryService;
import org.example.mypaintings.sale.dto.OrderDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderFacade Test Suite")
class OrderFacadeTest {

    private static final long HOTEL_ID = 42L;

    @Mock
    private PaintingPlacementQueryService paintingPlacementQueryService;

    @Mock
    private SaleQueryService saleQueryService;

    @InjectMocks
    private OrderFacade orderFacade;

    // ------------------------------------------------------------------
    // getOrdersForHotel
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("getOrdersForHotel")
    class GetOrdersForHotel {

        @Test
        @DisplayName("returns empty list when no orders need action")
        void shouldReturnEmptyListWhenNoPlacements() {
            // given
            when(paintingPlacementQueryService.getOrdersNeedingActionForHotel(HOTEL_ID))
                    .thenReturn(Collections.emptyList());

            // when
            List<OrderDto> result = orderFacade.getOrdersForHotel(HOTEL_ID);

            // then
            assertThat(result).isEmpty();
            verifyNoInteractions(saleQueryService);
        }

        @Test
        @DisplayName("maps placement and sale requiring shipping to OrderDto with address")
        void shouldMapPlacementAndSaleWithShipping() {
            // given
            long paintingId = 1L;
            Dimensions dims = new Dimensions(10, 20, Unit.CM);
            String imageKey = "key-1";
            String note = "premium placement";
            Instant saleDate = Instant.parse("2024-01-01T10:00:00Z");
            BuyerContactInfo buyer = new BuyerContactInfo("a@b.c", "123", "ul. Testowa 1");

            Painting painting = painting(paintingId, "Mona Lisa", imageKey, dims);
            PaintingPlacement placement = placement(painting, PlacementStatus.SENT_BY_HOTEL_TO_CLIENT, note);
            Sale sale = sale(true, buyer, saleDate);

            when(paintingPlacementQueryService.getOrdersNeedingActionForHotel(HOTEL_ID))
                    .thenReturn(List.of(placement));
            when(saleQueryService.getMappedSalesForPaintings(List.of(paintingId)))
                    .thenReturn(Map.of(paintingId, sale));

            // when
            List<OrderDto> result = orderFacade.getOrdersForHotel(HOTEL_ID);

            // then
            assertThat(result).hasSize(1);
            OrderDto dto = result.getFirst();

            assertThat(dto.paintingId()).isEqualTo(paintingId);
            assertThat(dto.title()).isEqualTo("Mona Lisa");
            assertThat(dto.imageFileKey()).isEqualTo(imageKey);
            assertThat(dto.note()).isEqualTo(note);
            assertThat(dto.dimensions()).isEqualTo(dims);
            assertThat(dto.saleDate()).isEqualTo(saleDate);
            assertThat(dto.fullAddress()).isEqualTo("ul. Testowa 1");
            assertThat(dto.possibleTransitions())
                    .containsExactly(PlacementStatus.RECEIVED_BY_CLIENT);
        }

        @Test
        @DisplayName("returns null fullAddress when shipping is not required")
        void shouldReturnNullAddressWhenShippingNotRequired() {
            // given
            long paintingId = 2L;
            Painting painting = painting(paintingId, "Starry Night", "key-2", new Dimensions(30, 40, Unit.CM));
            PaintingPlacement placement = placement(painting, PlacementStatus.WAITING_FOR_RECEPTION_BY_CLIENT, null);
            Sale sale = sale(false, new BuyerContactInfo("x@y.z", "999", "addr"), Instant.now());

            when(paintingPlacementQueryService.getOrdersNeedingActionForHotel(HOTEL_ID))
                    .thenReturn(List.of(placement));
            when(saleQueryService.getMappedSalesForPaintings(List.of(paintingId)))
                    .thenReturn(Map.of(paintingId, sale));

            // when
            List<OrderDto> result = orderFacade.getOrdersForHotel(HOTEL_ID);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.getFirst().fullAddress()).isNull();
            assertThat(result.getFirst().note()).isNull();
        }

        @Test
        @DisplayName("maps multiple placements correctly and filters hotel possible transitions")
        void shouldMapMultiplePlacementsInOrder() {
            // given
            Painting p1 = painting(10L, "A", "k10", new Dimensions(5, 5, Unit.CM));
            Painting p2 = painting(20L, "B", "k20", new Dimensions(6, 6, Unit.CM));

            PaintingPlacement pl1 = placement(p1, PlacementStatus.SENT_BY_HOTEL_TO_CLIENT, "note1");
            PaintingPlacement pl2 = placement(p2, PlacementStatus.WAITING_FOR_SHIPPING_TO_CLIENT, "note2");

            Sale s1 = sale(true, new BuyerContactInfo("e1", "p1", "addr1"), Instant.parse("2024-02-01T00:00:00Z"));
            Sale s2 = sale(false, null, Instant.parse("2024-02-02T00:00:00Z"));

            when(paintingPlacementQueryService.getOrdersNeedingActionForHotel(HOTEL_ID))
                    .thenReturn(List.of(pl1, pl2));
            when(saleQueryService.getMappedSalesForPaintings(List.of(10L, 20L)))
                    .thenReturn(Map.of(10L, s1, 20L, s2));

            // when
            List<OrderDto> result = orderFacade.getOrdersForHotel(HOTEL_ID);

            // then
            assertThat(result).hasSize(2);
            assertThat(result).extracting(OrderDto::paintingId).containsExactly(10L, 20L);

            assertThat(result.get(0).possibleTransitions())
                    .containsExactly(PlacementStatus.RECEIVED_BY_CLIENT);
            assertThat(result.get(1).possibleTransitions())
                    .containsExactly(PlacementStatus.SENT_BY_HOTEL_TO_CLIENT);

            assertThat(result.get(0).fullAddress()).isEqualTo("addr1");
            assertThat(result.get(1).fullAddress()).isNull();
        }

        @Test
        @DisplayName("passes correct painting IDs to SaleQueryService")
        void shouldQuerySalesForCorrectPaintingIds() {
            // given
            Painting p1 = painting(100L, "T1", "k1", new Dimensions(1, 1, Unit.CM));
            Painting p2 = painting(200L, "T2", "k2", new Dimensions(2, 2, Unit.CM));
            PaintingPlacement pl1 = placement(p1, PlacementStatus.WAITING_FOR_SHIPPING_TO_CLIENT, null);
            PaintingPlacement pl2 = placement(p2, PlacementStatus.WAITING_FOR_SHIPPING_TO_CLIENT, null);

            Sale sale1 = sale(false, null, Instant.now());
            Sale sale2 = sale(false, null, Instant.now());

            when(paintingPlacementQueryService.getOrdersNeedingActionForHotel(HOTEL_ID))
                    .thenReturn(List.of(pl1, pl2));
            when(saleQueryService.getMappedSalesForPaintings(anyList()))
                    .thenReturn(Map.of(100L, sale1, 200L, sale2));

            // when
            orderFacade.getOrdersForHotel(HOTEL_ID);

            // then
            verify(saleQueryService).getMappedSalesForPaintings(List.of(100L, 200L));
        }

        @Test
        @DisplayName("does not call SaleQueryService when placements list is empty")
        void shouldNotCallSaleQueryServiceOnEmptyPlacements() {
            // given
            when(paintingPlacementQueryService.getOrdersNeedingActionForHotel(HOTEL_ID))
                    .thenReturn(List.of());

            // when
            orderFacade.getOrdersForHotel(HOTEL_ID);

            // then
            verify(saleQueryService, never()).getMappedSalesForPaintings(any());
        }
    }

    // ------------------------------------------------------------------
    // Hotel Transitions
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Hotel Status Transitions")
    class HotelTransitions {

        @Test
        @DisplayName("WAITING_FOR_SHIPPING_TO_CLIENT maps to SENT_BY_HOTEL_TO_CLIENT")
        void fromWaitingForShippingToClient() {
            assertHotelTransitions(PlacementStatus.WAITING_FOR_SHIPPING_TO_CLIENT,
                    PlacementStatus.SENT_BY_HOTEL_TO_CLIENT);
        }

        @Test
        @DisplayName("WAITING_FOR_RECEPTION_BY_CLIENT maps to RECEIVED_BY_CLIENT")
        void fromWaitingForReceptionByClient() {
            assertHotelTransitions(PlacementStatus.WAITING_FOR_RECEPTION_BY_CLIENT,
                    PlacementStatus.RECEIVED_BY_CLIENT);
        }

        @Test
        @DisplayName("SENT_BY_HOTEL_TO_CLIENT maps to RECEIVED_BY_CLIENT")
        void fromSentByHotelToClient() {
            assertHotelTransitions(PlacementStatus.SENT_BY_HOTEL_TO_CLIENT,
                    PlacementStatus.RECEIVED_BY_CLIENT);
        }

        private void assertHotelTransitions(PlacementStatus current, PlacementStatus... expected) {
            // given
            Painting painting = painting(1L, "T", "k", new Dimensions(1, 1, Unit.CM));
            PaintingPlacement placement = placement(painting, current, null);
            Sale sale = sale(false, null, Instant.now());

            when(paintingPlacementQueryService.getOrdersNeedingActionForHotel(HOTEL_ID))
                    .thenReturn(List.of(placement));
            when(saleQueryService.getMappedSalesForPaintings(anyList()))
                    .thenReturn(Map.of(1L, sale));

            // when
            List<OrderDto> result = orderFacade.getOrdersForHotel(HOTEL_ID);

            // then
            assertThat(result.getFirst().possibleTransitions()).containsExactly(expected);
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Painting painting(long id, String title, String imageKey, Dimensions dims) {
        Painting p = mock(Painting.class);
        lenient().when(p.getId()).thenReturn(id);
        lenient().when(p.getTitle()).thenReturn(title);
        lenient().when(p.getFirstImageFileKey()).thenReturn(imageKey);
        lenient().when(p.getDimensions()).thenReturn(dims);
        return p;
    }

    private PaintingPlacement placement(Painting painting, PlacementStatus status, String note) {
        PaintingPlacement placement = mock(PaintingPlacement.class);
        lenient().when(placement.getPainting()).thenReturn(painting);
        lenient().when(placement.getPlacementStatus()).thenReturn(status);
        lenient().when(placement.getNote()).thenReturn(note);
        return placement;
    }

    private Sale sale(boolean requiresShipping, BuyerContactInfo buyer, Instant saleDate) {
        Sale sale = mock(Sale.class);
        lenient().when(sale.isRequiresShipping()).thenReturn(requiresShipping);
        lenient().when(sale.getBuyerContactInfo()).thenReturn(buyer);
        lenient().when(sale.getSaleDate()).thenReturn(saleDate);
        return sale;
    }
}
