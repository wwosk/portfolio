package org.example.mypaintings.sale;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.mypaintings.sale.dto.CreateSaleRequest;
import org.example.mypaintings.sale.dto.SaleOfferResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/sales")
@RequiredArgsConstructor
public class SaleController {

    private final SaleService saleService;

    @GetMapping("/paintings/{paintingId}/offer")
    public ResponseEntity<SaleOfferResponse> getSaleOffer(@PathVariable Long paintingId) {
        return ResponseEntity.ok(saleService.getSaleOffer(paintingId));
    }


    @PutMapping("/paintings/{paintingId}/lock")
    public ResponseEntity<Boolean> checkAndLockPainting(
            @PathVariable long paintingId) {
        boolean isAllowed = saleService.acquireLockForPainting(paintingId);

        return ResponseEntity.ok(isAllowed);
    }

    @PostMapping
    public ResponseEntity<Void> createSaleDirectly(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody @Valid CreateSaleRequest request) {

        saleService.createSaleEntry(request, idempotencyKey);
        return ResponseEntity.status(201).build();
    }


}