package org.example.mypaintings.painting;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.mypaintings.auth.CurrentUserId;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/paintings")
@RequiredArgsConstructor
public class PaintingController {

    private final PaintingService paintingRegistryService;

    @SecurityRequirement(name = "bearerAuth")
    @PostMapping
    public ResponseEntity<Void> createPainting(
            @RequestBody @Valid PaintingRegistryRequest paintingDTO,
            @Parameter(hidden = true) @CurrentUserId Long artistId) {
        paintingRegistryService.createPainting(paintingDTO, artistId);
        return ResponseEntity.status(201).build();
    }


    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/{paintingId}")
    public ResponseEntity<Void> deletePainting(
            @PathVariable long paintingId,
            @Parameter(hidden = true) @CurrentUserId Long artistId) {

        paintingRegistryService.archivePainting(paintingId, artistId);
        return ResponseEntity.noContent().build();
    }
}