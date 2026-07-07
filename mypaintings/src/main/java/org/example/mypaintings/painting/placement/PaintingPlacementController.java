package org.example.mypaintings.painting.placement;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.mypaintings.auth.CurrentUserId;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/placements/{paintingId}")
@RequiredArgsConstructor
public class PaintingPlacementController {

    private final PaintingPlacementService paintingPlacementService;

    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/exhibit")
    public ResponseEntity<Void> exhibit(
            @PathVariable long paintingId,
            @Parameter(hidden = true) @CurrentUserId Long userId) {

        paintingPlacementService.exhibitPainting(paintingId, userId);
        return ResponseEntity.ok().build();
    }
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/send-to-client")
    public ResponseEntity<Void> sendToClient(
            @PathVariable long paintingId,
            @Parameter(hidden = true) @CurrentUserId Long userId) {

        paintingPlacementService.sendToClient(paintingId, userId);
        return ResponseEntity.ok().build();
    }
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/confirm-received-by-client")
    public ResponseEntity<Void> confirmReceived(
            @PathVariable long paintingId,
            @Parameter(hidden = true) @CurrentUserId Long userId) {

            paintingPlacementService.confirmReceptionByClient(paintingId, userId);
        return ResponseEntity.ok().build();
    }
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/return-to-artist")
    public ResponseEntity<Void> returnToArtist(
            @PathVariable long paintingId,
            @Parameter(hidden = true) @CurrentUserId Long userId) {

        paintingPlacementService.returnToArtist(paintingId, userId);
        return ResponseEntity.ok().build();
    }

    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/send-to-artist")
    public ResponseEntity<Void> sendToArtist(
            @PathVariable long paintingId,
            @Parameter(hidden = true) @CurrentUserId Long userId) {

        paintingPlacementService.sendToArtist(paintingId, userId);
        return ResponseEntity.ok().build();
    }
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/revoke")
    public ResponseEntity<Void> revokeByArtist(
            @PathVariable long paintingId,
            @RequestParam(defaultValue = "false") boolean deliveryNeeded,
            @Parameter(hidden = true) @CurrentUserId Long userId) {

        paintingPlacementService.revokeByArtist(paintingId, userId, deliveryNeeded);
        return ResponseEntity.ok().build();
    }
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/add-note")
    public ResponseEntity<Void> addNote(
            @PathVariable long paintingId,
            @RequestBody @Valid AddNoteRequest request,
            @Parameter(hidden = true) @CurrentUserId Long hotelId) {

        paintingPlacementService.addNote(hotelId, paintingId, request);
        return ResponseEntity.ok().build();
    }
}