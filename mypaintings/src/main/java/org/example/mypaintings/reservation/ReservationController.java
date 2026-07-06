package org.example.mypaintings.reservation;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.example.mypaintings.auth.CurrentUserId;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;


    @PostMapping("/{reservationId}/accept")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Void> accept(
            @PathVariable long reservationId,
            @Parameter(hidden = true) @CurrentUserId Long artistId) {

        reservationService.acceptReservationOffer(artistId, reservationId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{reservationId}/reject")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Void> reject(
            @PathVariable long reservationId,
            @Parameter(hidden = true) @CurrentUserId Long artistId) {

        reservationService.rejectReservationOffer(artistId, reservationId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{reservationId}/revoke")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Void> revoke(
            @PathVariable long reservationId,
            @Parameter(hidden = true) @CurrentUserId Long userId) {

        reservationService.revokeReservation(userId, reservationId);
        return ResponseEntity.ok().build();
    }


    @PostMapping("/{reservationId}/cancel")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Void> cancel(
            @PathVariable long reservationId,
            @Parameter(hidden = true) @CurrentUserId Long hotelId) {

        reservationService.cancelReservationOffer(hotelId, reservationId);
        return ResponseEntity.ok().build();
    }


    @PostMapping("/paintings/{paintingId}")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Void> createOffer(
            @PathVariable long paintingId,
            @Parameter(hidden = true) @CurrentUserId Long hotelId) {

        reservationService.createReservationOffer(hotelId, paintingId);
        return ResponseEntity.status(201).build();
    }


}