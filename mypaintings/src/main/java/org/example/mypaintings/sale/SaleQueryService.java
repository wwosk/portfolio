package org.example.mypaintings.sale;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;



@Transactional(readOnly = true)
@Service
@RequiredArgsConstructor
public class SaleQueryService {

    private final SaleRepository saleRepository;

    public List<Sale> getSalesByArtistId(long artistId) {
        return saleRepository.findAllByPaintingArtistIdWithPaintingAndAttachments(artistId);
    }

    public Map<Long, Sale> getMappedSalesForPaintings(List<Long> paintingIds) {
        if (paintingIds == null || paintingIds.isEmpty()) {
            return Map.of();
        }
        return saleRepository.findAllByPaintingIdInWithDetails(paintingIds).stream()
                .collect(Collectors.toMap(sale -> sale.getPainting().getId(), sale -> sale));
    }
}
