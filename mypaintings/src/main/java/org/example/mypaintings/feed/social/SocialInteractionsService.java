package org.example.mypaintings.feed.social;

import lombok.RequiredArgsConstructor;
import org.example.mypaintings.exception.validation.BusinessValidationException;
import org.example.mypaintings.feed.social.dto.ArtistStatsResponse;
import org.example.mypaintings.feed.social.dto.FollowedArtistResponse;
import org.example.mypaintings.painting.Painting;
import org.example.mypaintings.painting.PaintingQueryService;
import org.example.mypaintings.user.User;

import org.example.mypaintings.user.UserQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;


@Service
@RequiredArgsConstructor
@Transactional
public class SocialInteractionsService {
    private final UserQueryService userQueryService;
    private final PaintingLikeRepository paintingLikeRepository;
    private final FollowedArtistRepository followedArtistRepository;
    private final PaintingQueryService paintingQueryService;

    public void likePostToggle(long paintingId, long hotelId) {
        Painting painting = paintingQueryService.getActiveOrThrow(paintingId);
        User hotel = userQueryService.getValidHotelOrThrow(hotelId);

        Optional<PaintingLike> existingLike = paintingLikeRepository.findByPaintingAndHotel(painting, hotel);
        if (existingLike.isPresent()) {
            paintingLikeRepository.delete(existingLike.get());
        } else {
            PaintingLike newLike = new PaintingLike();
            newLike.setPainting(painting);
            newLike.setHotel(hotel);
            paintingLikeRepository.save(newLike);
        }
    }

    public void followArtist(long artistId, long hotelId) {
        User hotel = userQueryService.getValidHotelOrThrow(hotelId);
        User artist = userQueryService.getValidArtistOrThrow(artistId);


        boolean alreadyFollowing = followedArtistRepository.existsByHotelAndArtist(hotel, artist);
        if (alreadyFollowing) throw new BusinessValidationException("Already following artist");

        boolean notTheSameAccounts = !hotel.getId().equals(artist.getId());
        if (!notTheSameAccounts) throw new BusinessValidationException("Cannot follow own account");

        FollowedArtist followedArtist = new FollowedArtist();
        followedArtist.setHotel(hotel);
        followedArtist.setArtist(artist);
        followedArtistRepository.save(followedArtist);


    }

    public void unfollowArtist(long artistId, long hotelId) {
        User hotel = userQueryService.getValidHotelOrThrow(hotelId);
        User artist = userQueryService.getValidArtistOrThrow(artistId);

        followedArtistRepository.deleteByHotelAndArtist(hotel, artist);
    }

    public ArtistStatsResponse getArtistStats(long artistId) {
        User artist = userQueryService.getValidArtistOrThrow(artistId);

        long followersCount = followedArtistRepository.countByArtistId(artistId);
        long paintingsCount = paintingQueryService.getArtistPaintingCount(artistId);

        return new ArtistStatsResponse(artist.getNickname(), followersCount, paintingsCount);
    }

    public List<FollowedArtistResponse> getFollowedArtists(long hotelId) {
        userQueryService.getValidHotelOrThrow(hotelId);
        List<FollowedArtist> followedList = followedArtistRepository.findAllByHotelId(hotelId);

        return followedList.stream().map(fa -> {
            User artist = fa.getArtist();
            String nickname = artist.getNickname();
            String initials = generateInitials(nickname);
            return new FollowedArtistResponse(artist.getId(), nickname, initials);
        }).toList();
    }

    private String generateInitials(String nickname) {
        if (nickname == null || nickname.isBlank()) return "AT";
        String clean = nickname.replaceAll("^@", "");
        if (clean.length() >= 2) {
            return clean.substring(0, 2).toUpperCase();
        }
        return clean.toUpperCase();
    }






}