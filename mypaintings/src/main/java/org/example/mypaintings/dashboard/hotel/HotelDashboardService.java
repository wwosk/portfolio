package org.example.mypaintings.dashboard.hotel;
import lombok.RequiredArgsConstructor;
import org.example.mypaintings.dashboard.hotel.dto.ReservationDashboardResponse;
import org.example.mypaintings.dashboard.hotel.dto.OrderDashboardResponse;
import org.example.mypaintings.dashboard.hotel.dto.PaintingStockDashboardResponse;
import org.example.mypaintings.painting.placement.PaintingPlacement;
import org.example.mypaintings.painting.placement.PaintingPlacementQueryService;
import org.example.mypaintings.painting.placement.ReturnDeliveryInfo;
import org.example.mypaintings.painting.placement.ReturnDeliveryInfoRepository;
import org.example.mypaintings.reservation.ReservationOffer;
import org.example.mypaintings.reservation.ReservationQueryService;
import org.example.mypaintings.reservation.ReservationStatus;
import org.example.mypaintings.sale.financial.OrderFacade;

import org.example.mypaintings.painting.attachedimages.FileUrlService;
import org.example.mypaintings.sale.dto.OrderDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HotelDashboardService {
    private final OrderFacade orderFacade;
    private final FileUrlService fileUrlService;
    private final ReservationQueryService reservationQueryService;
    private final PaintingPlacementQueryService paintingPlacementQueryService;
    private final ReturnDeliveryInfoRepository returnDeliveryInfoRepository;


    public List<OrderDashboardResponse> getOrders(long hotelId) {
        List<OrderDto> orders = orderFacade.getOrdersForHotel(hotelId);

        return orders.stream()
                .map(order -> OrderDashboardResponse.builder()
                        .paintingId(order.paintingId())
                        .title(order.title())
                        .imageUrl(fileUrlService.buildUrl(order.imageFileKey()))
                        .note(order.note())
                        .dimensions(order.dimensions())
                        .possibleTransitions(order.possibleTransitions())
                        .fullAddress(order.fullAddress())
                        .saleDate(order.saleDate())
                        .build())
                .toList();
    }


    public List<PaintingStockDashboardResponse> getPaintingOnStock(long hotelId) {
        List<PaintingPlacement> paintingsOnStock = paintingPlacementQueryService.getPaintingsOnStockForHotel(hotelId);

        if (paintingsOnStock.isEmpty()) {
            return List.of();
        }

        List<Long> placementIds = paintingsOnStock.stream()
                .map(PaintingPlacement::getId)
                .toList();

        Map<Long, String> returnAddressesMap = returnDeliveryInfoRepository
                .findAllByPlacementIdIn(placementIds)
                .stream()
                .collect(Collectors.toMap(
                        info -> info.getPlacement().getId(),
                        ReturnDeliveryInfo::getFullAddress,
                        (existing, _) -> existing
                ));



        return paintingsOnStock.stream()
                .map(placement -> {
                    var painting = placement.getPainting();
                    String imageUrl = fileUrlService.buildUrl(painting.getFirstImageFileKey());
                    String fullAddress = returnAddressesMap.get(placement.getId());

                    return PaintingStockDashboardResponse.builder()
                            .paintingId(painting.getId())
                            .title(painting.getTitle())
                            .imageUrl(imageUrl)
                            .note(placement.getNote())
                            .dimensions(painting.getDimensions())
                            .possibleTransitions(placement.getPlacementStatus().getPossibleTransitionsForHotel())
                            .currentStatus(placement.getPlacementStatus())
                            .placementDate(placement.getPlacementDate())
                            .fullAddress(fullAddress)
                            .build();
                })
                .toList();
    }


    public List<ReservationDashboardResponse> getReservations(long hotelId) {
        List<ReservationOffer> allOffers = reservationQueryService.getOffersByHotelId(hotelId);
        return allOffers.stream()
                .map(offer -> {
                    var painting = offer.getPainting();
                    String imageUrl = fileUrlService.buildUrl(painting.getFirstImageFileKey());
                    boolean isPending = offer.getStatus() == ReservationStatus.PENDING;

                    return ReservationDashboardResponse.builder()
                            .reservationId(offer.getId())
                            .paintingId(offer.getPainting().getId())
                            .artistId(painting.getArtist().getId())
                            .title(painting.getTitle())
                            .imageUrl(imageUrl)
                            .dimensions(painting.getDimensions())
                            .status(offer.getStatus())
                            .isCancellable(isPending)
                            .build();
                })
                .toList();
    }
}