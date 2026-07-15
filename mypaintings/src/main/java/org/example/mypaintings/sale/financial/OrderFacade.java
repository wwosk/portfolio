package org.example.mypaintings.sale.financial;

import lombok.RequiredArgsConstructor;
import org.example.mypaintings.painting.placement.PaintingPlacement;
import org.example.mypaintings.painting.placement.PaintingPlacementQueryService;

import org.example.mypaintings.sale.Sale;
import org.example.mypaintings.sale.SaleQueryService;
import org.example.mypaintings.sale.dto.OrderDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;


@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderFacade {
    private final PaintingPlacementQueryService paintingPlacementQueryService;
    private final SaleQueryService saleQueryService;

    public List<OrderDto> getOrdersForHotel(long hotelId) {
        List<PaintingPlacement> placements = paintingPlacementQueryService.getOrdersNeedingActionForHotel(hotelId);

        if (placements.isEmpty()) {
            return List.of();
        }

        List<Long> paintingIds = placements.stream()
                .map(p -> p.getPainting().getId())
                .toList();

        Map<Long, Sale> salesMap = saleQueryService.getMappedSalesForPaintings(paintingIds);

        return placements.stream()
                .map(placement -> {
                    var painting = placement.getPainting();
                    Sale sale = salesMap.get(painting.getId());

                    return new OrderDto(
                            painting.getId(),
                            painting.getTitle(),
                            painting.getFirstImageFileKey(),
                            placement.getNote(),
                            painting.getDimensions(),
                            placement.getPlacementStatus().getPossibleTransitionsForHotel(),
                            (sale.isRequiresShipping()) ? sale.getBuyerContactInfo().getFullAddress() : null,
                            sale.getSaleDate()
                    );
                })
                .toList();
    }
}
