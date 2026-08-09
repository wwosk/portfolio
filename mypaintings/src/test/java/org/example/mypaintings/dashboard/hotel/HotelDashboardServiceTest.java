package org.example.mypaintings.dashboard.hotel;

import org.example.mypaintings.dashboard.hotel.dto.OrderDashboardResponse;
import org.example.mypaintings.dashboard.hotel.dto.PaintingStockDashboardResponse;
import org.example.mypaintings.dashboard.hotel.dto.ReservationDashboardResponse;
import org.example.mypaintings.painting.Painting;
import org.example.mypaintings.painting.attachedimages.FileUrlService;
import org.example.mypaintings.painting.features.Dimensions;
import org.example.mypaintings.painting.features.Unit;
import org.example.mypaintings.painting.placement.*;
import org.example.mypaintings.reservation.ReservationOffer;
import org.example.mypaintings.reservation.ReservationQueryService;
import org.example.mypaintings.reservation.ReservationStatus;
import org.example.mypaintings.sale.dto.OrderDto;
import org.example.mypaintings.sale.financial.OrderFacade;
import org.example.mypaintings.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HotelDashboardServiceTest {

    @Mock OrderFacade orderFacade;
    @Mock FileUrlService fileUrlService;
    @Mock ReservationQueryService reservationQueryService;
    @Mock PaintingPlacementQueryService paintingPlacementQueryService;
    @Mock ReturnDeliveryInfoRepository returnDeliveryInfoRepository;

    @InjectMocks HotelDashboardService service;

    private static final long HOTEL_ID = 10L;

    private Dimensions dimensions() {
        return new Dimensions(100.0, 80.0, Unit.CM);
    }

    private OrderDto orderDto(Long paintingId, String imageKey, String fullAddress, Instant saleDate) {
        return new OrderDto(
                paintingId,
                "Title " + paintingId,
                imageKey,
                "note",
                dimensions(),
                List.of(PlacementStatus.WAITING_FOR_RECEPTION_BY_CLIENT),
                fullAddress,
                saleDate
        );
    }

    private Painting mockPainting(long id, String title, String imageKey) {
        Painting p = mock(Painting.class);
        when(p.getId()).thenReturn(id);
        when(p.getTitle()).thenReturn(title);
        when(p.getFirstImageFileKey()).thenReturn(imageKey);
        when(p.getDimensions()).thenReturn(dimensions());
        return p;
    }

    private PaintingPlacement mockPlacement(Painting painting, Instant placementDate) {
        PaintingPlacement placement = mock(PaintingPlacement.class);
        when(placement.getPainting()).thenReturn(painting);
        when(placement.getPlacementStatus()).thenReturn(PlacementStatus.EXHIBITED);
        when(placement.getNote()).thenReturn("on wall");
        when(placement.getPlacementDate()).thenReturn(placementDate);
        return placement;
    }

    private ReservationOffer mockOffer(long id, Painting painting, ReservationStatus status) {
        ReservationOffer offer = mock(ReservationOffer.class);
        when(offer.getId()).thenReturn(id);
        when(offer.getPainting()).thenReturn(painting);
        when(offer.getStatus()).thenReturn(status);
        return offer;
    }

    @Nested
    @DisplayName("getOrders")
    class GetOrders {

        @Test
        @DisplayName("Should map OrderDto to OrderDashboardResponse and build image URL correctly")
        void getOrders_shouldMapFieldsAndBuildImageUrl() {
            // given
            Instant saleDate = Instant.parse("2026-01-15T10:00:00Z");
            OrderDto dto = orderDto(1L, "paintings/1.jpg", "Main St 1", saleDate);

            when(orderFacade.getOrdersForHotel(HOTEL_ID)).thenReturn(List.of(dto));
            when(fileUrlService.buildUrl("paintings/1.jpg")).thenReturn("http://cdn/paintings/1.jpg");

            // when
            List<OrderDashboardResponse> result = service.getOrders(HOTEL_ID);

            // then
            assertThat(result).hasSize(1);
            OrderDashboardResponse r = result.getFirst();
            assertThat(r.paintingId()).isEqualTo(1L);
            assertThat(r.title()).isEqualTo("Title 1");
            assertThat(r.imageUrl()).isEqualTo("http://cdn/paintings/1.jpg");
            assertThat(r.note()).isEqualTo("note");
            assertThat(r.fullAddress()).isEqualTo("Main St 1");
            assertThat(r.saleDate()).isEqualTo(saleDate);
            assertThat(r.dimensions()).isEqualTo(dto.dimensions());
            assertThat(r.possibleTransitions()).containsExactly(PlacementStatus.WAITING_FOR_RECEPTION_BY_CLIENT);

            verify(orderFacade).getOrdersForHotel(HOTEL_ID);
            verify(fileUrlService).buildUrl("paintings/1.jpg");
        }

        @Test
        @DisplayName("Should return empty list when hotel has no orders")
        void getOrders_shouldReturnEmpty_whenNoOrders() {
            // given
            when(orderFacade.getOrdersForHotel(HOTEL_ID)).thenReturn(List.of());

            // when
            List<OrderDashboardResponse> result = service.getOrders(HOTEL_ID);

            // then
            assertThat(result).isEmpty();
            verifyNoInteractions(fileUrlService);
        }

        @Test
        @DisplayName("Should map multiple orders preserving their original sequence")
        void getOrders_shouldMapAllOrdersInOrder() {
            // given
            OrderDto a = orderDto(1L, "a.jpg", "A", Instant.now());
            OrderDto b = orderDto(2L, "b.jpg", "B", Instant.now());
            when(orderFacade.getOrdersForHotel(HOTEL_ID)).thenReturn(List.of(a, b));
            when(fileUrlService.buildUrl(anyString())).thenAnswer(inv -> "url:" + inv.getArgument(0));

            // when
            List<OrderDashboardResponse> result = service.getOrders(HOTEL_ID);

            // then
            assertThat(result).extracting(OrderDashboardResponse::paintingId)
                    .containsExactly(1L, 2L);
        }
    }

    @Nested
    @DisplayName("getPaintingOnStock")
    class GetPaintingOnStock {

        @Test
        @DisplayName("Should map placement and painting details correctly when stock is present")
        void getPaintingOnStock_shouldMapFields() {
            // given
            Instant placementDate = Instant.parse("2026-02-01T08:00:00Z");
            Painting painting = mockPainting(5L, "Sunset", "paintings/5.jpg");
            PaintingPlacement placement = mockPlacement(painting, placementDate);

            when(paintingPlacementQueryService.getPaintingsOnStockForHotel(HOTEL_ID))
                    .thenReturn(List.of(placement));
            when(fileUrlService.buildUrl("paintings/5.jpg")).thenReturn("http://cdn/paintings/5.jpg");
            when(returnDeliveryInfoRepository.findAllByPlacementIdIn(anyCollection()))
                    .thenReturn(List.of());

            // when
            List<PaintingStockDashboardResponse> result = service.getPaintingOnStock(HOTEL_ID);

            // then
            assertThat(result).hasSize(1);
            PaintingStockDashboardResponse r = result.getFirst();
            assertThat(r.paintingId()).isEqualTo(5L);
            assertThat(r.title()).isEqualTo("Sunset");
            assertThat(r.imageUrl()).isEqualTo("http://cdn/paintings/5.jpg");
            assertThat(r.note()).isEqualTo("on wall");
            assertThat(r.currentStatus()).isEqualTo(PlacementStatus.EXHIBITED);
            assertThat(r.placementDate()).isEqualTo(placementDate);
            assertThat(r.dimensions()).isEqualTo(painting.getDimensions());
            assertThat(r.fullAddress()).isNull();
            assertThat(r.possibleTransitions())
                    .isEqualTo(PlacementStatus.EXHIBITED.getPossibleTransitionsForHotel());

            verify(paintingPlacementQueryService).getPaintingsOnStockForHotel(HOTEL_ID);
            verify(returnDeliveryInfoRepository).findAllByPlacementIdIn(anyCollection());
        }

        @Test
        @DisplayName("Should map full return address from ReturnDeliveryInfo when available")
        void returnDeliveryInfo_shouldMapFullAddressWhenPresent() {
            // given
            Instant placementDate = Instant.parse("2026-02-01T08:00:00Z");
            Painting painting = mockPainting(5L, "Sunset", "paintings/5.jpg");
            PaintingPlacement placement = mockPlacement(painting, placementDate);
            when(placement.getId()).thenReturn(10L);

            ReturnDeliveryInfo deliveryInfo = mock(ReturnDeliveryInfo.class);
            when(deliveryInfo.getPlacement()).thenReturn(placement);
            when(deliveryInfo.getFullAddress()).thenReturn("Warehouse St 123");

            when(paintingPlacementQueryService.getPaintingsOnStockForHotel(HOTEL_ID))
                    .thenReturn(List.of(placement));
            when(fileUrlService.buildUrl("paintings/5.jpg")).thenReturn("http://cdn/paintings/5.jpg");
            when(returnDeliveryInfoRepository.findAllByPlacementIdIn(anyCollection()))
                    .thenReturn(List.of(deliveryInfo));

            // when
            List<PaintingStockDashboardResponse> result = service.getPaintingOnStock(HOTEL_ID);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.getFirst().fullAddress()).isEqualTo("Warehouse St 123");

            verify(returnDeliveryInfoRepository).findAllByPlacementIdIn(anyCollection());
        }

        @Test
        @DisplayName("Should return empty list when hotel has no paintings on stock")
        void getPaintingOnStock_shouldReturnEmpty_whenNoStock() {
            // given
            when(paintingPlacementQueryService.getPaintingsOnStockForHotel(HOTEL_ID))
                    .thenReturn(List.of());

            // when
            List<PaintingStockDashboardResponse> result = service.getPaintingOnStock(HOTEL_ID);

            // then
            assertThat(result).isEmpty();
            verifyNoInteractions(fileUrlService);
        }
    }

    @Nested
    @DisplayName("getReservations")
    class GetReservations {

        @Test
        @DisplayName("Should mark PENDING reservation offer as cancellable")
        void getReservations_shouldMarkPendingAsCancellable() {
            // given
            Painting painting = mockPainting(3L, "Portrait", "paintings/3.jpg");
            User artist = mock(User.class);
            when(artist.getId()).thenReturn(77L);
            when(painting.getArtist()).thenReturn(artist);

            ReservationOffer offer = mockOffer(100L, painting, ReservationStatus.PENDING);

            when(reservationQueryService.getOffersByHotelId(HOTEL_ID)).thenReturn(List.of(offer));
            when(fileUrlService.buildUrl("paintings/3.jpg")).thenReturn("http://cdn/paintings/3.jpg");

            // when
            List<ReservationDashboardResponse> result = service.getReservations(HOTEL_ID);

            // then
            assertThat(result).hasSize(1);
            ReservationDashboardResponse r = result.getFirst();
            assertThat(r.reservationId()).isEqualTo(100L);
            assertThat(r.paintingId()).isEqualTo(3L);
            assertThat(r.artistId()).isEqualTo(77L);
            assertThat(r.title()).isEqualTo("Portrait");
            assertThat(r.imageUrl()).isEqualTo("http://cdn/paintings/3.jpg");
            assertThat(r.status()).isEqualTo(ReservationStatus.PENDING);
            assertThat(r.isCancellable()).isTrue();
        }

        @Test
        @DisplayName("Should not mark non-PENDING reservation offer as cancellable")
        void getReservations_shouldNotMarkNonPendingAsCancellable() {
            // given
            Painting painting = mockPainting(3L, "Portrait", "paintings/3.jpg");
            User artist = mock(User.class);
            when(artist.getId()).thenReturn(77L);
            when(painting.getArtist()).thenReturn(artist);

            ReservationOffer offer = mockOffer(100L, painting, ReservationStatus.ACCEPTED);

            when(reservationQueryService.getOffersByHotelId(HOTEL_ID)).thenReturn(List.of(offer));
            when(fileUrlService.buildUrl("paintings/3.jpg")).thenReturn("http://cdn/paintings/3.jpg");

            // when
            List<ReservationDashboardResponse> result = service.getReservations(HOTEL_ID);

            // then
            assertThat(result.getFirst().isCancellable()).isFalse();
            assertThat(result.getFirst().status()).isEqualTo(ReservationStatus.ACCEPTED);
        }

        @Test
        @DisplayName("Should return empty list when hotel has no reservation offers")
        void getReservations_shouldReturnEmpty_whenNoOffers() {
            // given
            when(reservationQueryService.getOffersByHotelId(HOTEL_ID)).thenReturn(List.of());

            // when
            List<ReservationDashboardResponse> result = service.getReservations(HOTEL_ID);

            // then
            assertThat(result).isEmpty();
            verifyNoInteractions(fileUrlService);
        }

        @Test
        @DisplayName("Should build specific image URL per each offer when mapping multiple reservations")
        void getReservations_shouldBuildImageUrlPerOffer() {
            // given
            User artist = mock(User.class);
            when(artist.getId()).thenReturn(77L);

            Painting p1 = mockPainting(1L, "A", "a.jpg");
            Painting p2 = mockPainting(2L, "B", "b.jpg");
            when(p1.getArtist()).thenReturn(artist);
            when(p2.getArtist()).thenReturn(artist);

            ReservationOffer o1 = mockOffer(1L, p1, ReservationStatus.PENDING);
            ReservationOffer o2 = mockOffer(2L, p2, ReservationStatus.PENDING);

            when(reservationQueryService.getOffersByHotelId(HOTEL_ID)).thenReturn(List.of(o1, o2));
            when(fileUrlService.buildUrl("a.jpg")).thenReturn("url:a");
            when(fileUrlService.buildUrl("b.jpg")).thenReturn("url:b");

            // when
            List<ReservationDashboardResponse> result = service.getReservations(HOTEL_ID);

            // then
            assertThat(result).extracting(ReservationDashboardResponse::imageUrl)
                    .containsExactly("url:a", "url:b");
            verify(fileUrlService, times(2)).buildUrl(anyString());
        }
    }
}