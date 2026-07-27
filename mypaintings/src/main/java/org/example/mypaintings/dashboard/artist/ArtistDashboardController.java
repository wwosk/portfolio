package org.example.mypaintings.dashboard.artist;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.example.mypaintings.dashboard.artist.dto.PlacementDashboardResponse;
import org.example.mypaintings.dashboard.artist.dto.PostDashboardResponse;
import org.example.mypaintings.dashboard.artist.dto.ReservationDashboardResponse;
import org.example.mypaintings.dashboard.artist.dto.SaleDashboardResponse;
import org.example.mypaintings.auth.CurrentUserId;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/artist/dashboard")
@RequiredArgsConstructor
public class ArtistDashboardController {

    private final ArtistDashboardService dashboardService;


    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/sales")
    public ResponseEntity<List<SaleDashboardResponse>> getSales(@Parameter(hidden = true) @CurrentUserId Long artistId) {
        return ResponseEntity.ok(dashboardService.getArtistSales(artistId));
    }

    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/reservations")
    public ResponseEntity<List<ReservationDashboardResponse>> getReservations(@Parameter(hidden = true) @CurrentUserId Long artistId) {
        return ResponseEntity.ok(dashboardService.getPaintingsReservations(artistId));
    }


    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/placements")
    public ResponseEntity<List<PlacementDashboardResponse>> getPlacements(@Parameter(hidden = true) @CurrentUserId Long artistId) {
        return ResponseEntity.ok(dashboardService.getActivePaintingsPlacements(artistId));
    }

    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/posts")
    public ResponseEntity<List<PostDashboardResponse>> getPosts(@Parameter(hidden = true) @CurrentUserId Long artistId) {
        return ResponseEntity.ok(dashboardService.getArtistPosts(artistId));
    }



}