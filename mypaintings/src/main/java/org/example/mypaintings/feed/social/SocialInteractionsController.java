package org.example.mypaintings.feed.social;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.example.mypaintings.auth.CurrentUserId;
import org.example.mypaintings.feed.social.dto.ArtistStatsResponse;
import org.example.mypaintings.feed.social.dto.FollowedArtistResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/api/social")
@RequiredArgsConstructor
public class SocialInteractionsController {

    private final SocialInteractionsService socialInteractionsService;

    @PutMapping("/paintings/{paintingId}/like-toggle")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Void> toggleLike(
            @PathVariable long paintingId,
            @Parameter(hidden = true) @CurrentUserId Long hotelId) {

        socialInteractionsService.likePostToggle(paintingId, hotelId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/artists/{artistId}/follow")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Void> followArtist(
            @PathVariable long artistId,
            @Parameter(hidden = true) @CurrentUserId Long hotelId) {

        socialInteractionsService.followArtist(artistId, hotelId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/artists/{artistId}/unfollow")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Void> unfollowArtist(
            @PathVariable long artistId,
       @Parameter(hidden = true) @CurrentUserId Long hotelId) {

        socialInteractionsService.unfollowArtist(artistId, hotelId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/artists/followed")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<FollowedArtistResponse>> getFollowedArtists(@Parameter(hidden = true) @CurrentUserId Long hotelId) {
        List<FollowedArtistResponse> followed = socialInteractionsService.getFollowedArtists(hotelId);
        return ResponseEntity.ok(followed);
    }

    @GetMapping("/artists/{artistId}/stats")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ArtistStatsResponse> getArtistStats(@PathVariable long artistId) {
        ArtistStatsResponse stats = socialInteractionsService.getArtistStats(artistId);
        return ResponseEntity.ok(stats);
    }
}
