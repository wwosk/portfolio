package org.example.mypaintings.feed;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.example.mypaintings.feed.dto.PaintingFilterCriteria;
import org.example.mypaintings.painting.features.Orientation;
import org.example.mypaintings.painting.features.PaintingMedium;
import org.example.mypaintings.feed.dto.PaintingDetailsResponse;
import org.example.mypaintings.feed.dto.PaintingFeedItemResponse;
import org.example.mypaintings.painting.styles.PaintingStyle;
import org.example.mypaintings.painting.features.SizeCategory;
import org.example.mypaintings.auth.CurrentUserId;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/api/feed")
@RequiredArgsConstructor
public class HotelFeedController {

    private final PaintingFeedService paintingFeedService;

    @SecurityRequirement(name = "bearerAuth")
    @GetMapping
    public ResponseEntity<Slice<PaintingFeedItemResponse>> getHotelPaintingFeed(
            @Parameter(hidden = true) @CurrentUserId Long hotelId,

            @RequestParam(required = false) Double radiusKm,
            @RequestParam(required = false) PaintingStyle[] style,
            @RequestParam(required = false) SizeCategory[] sizecategory,
            @RequestParam(required = false) PaintingMedium[] medium,
            @RequestParam(required = false) Orientation[] orientation,
            @PageableDefault(size = 20) Pageable pageable) {

        var criteria = new PaintingFilterCriteria(
                radiusKm,
                style != null ? List.of(style) : null,
                sizecategory != null ? List.of(sizecategory) : null,
                medium != null ? List.of(medium) : null,
                orientation != null ? List.of(orientation) : null
        );

        Slice<PaintingFeedItemResponse> feed = paintingFeedService.providePaintingFeed(hotelId, criteria, pageable);
        return ResponseEntity.ok(feed);
    }

    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/artist/{artistId}")
    public ResponseEntity<Slice<PaintingFeedItemResponse>> getArtistProfilePaintings(
            @Parameter(hidden = true) @CurrentUserId Long hotelId,

            @PathVariable long artistId,
            @PageableDefault(size = 20) Pageable pageable) {

        Slice<PaintingFeedItemResponse> artistPaintings = paintingFeedService.provideArtistProfilePaintings(hotelId,artistId,pageable);
        return ResponseEntity.ok(artistPaintings);
    }

    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/paintings/{paintingId}")
    public ResponseEntity<PaintingDetailsResponse> getPaintingDetails(
            @PathVariable long paintingId,
            @Parameter(hidden = true) @CurrentUserId Long hotelId) {

        PaintingDetailsResponse details = paintingFeedService.getPaintingDetails(paintingId, hotelId);
        return ResponseEntity.ok(details);
    }


}