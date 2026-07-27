package org.example.mypaintings.dashboard.hotel;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.example.mypaintings.dashboard.hotel.dto.OrderDashboardResponse;
import org.example.mypaintings.dashboard.hotel.dto.PaintingStockDashboardResponse;
import org.example.mypaintings.dashboard.hotel.dto.ReservationDashboardResponse;
import org.example.mypaintings.auth.CurrentUserId;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/hotel/dashboard")
@RequiredArgsConstructor
public class HotelDashboardController {

    private final HotelDashboardService hotelDashboardService;


    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/orders")
    public ResponseEntity<List<OrderDashboardResponse>> getOrders(@Parameter(hidden = true) @CurrentUserId Long hotelId) {
        return ResponseEntity.ok(hotelDashboardService.getOrders(hotelId));
    }

    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/stock")
    public ResponseEntity<List<PaintingStockDashboardResponse>> getPaintingOnStock(@Parameter(hidden = true) @CurrentUserId Long hotelId) {
        return ResponseEntity.ok(hotelDashboardService.getPaintingOnStock(hotelId));
    }

    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/reservations/pending")
    public ResponseEntity<List<ReservationDashboardResponse>> getPendingReservations(@Parameter(hidden = true) @CurrentUserId Long hotelId) {
        return ResponseEntity.ok(hotelDashboardService.getReservations(hotelId));
    }
}