package org.example.mypaintings.feed.social;


import org.example.mypaintings.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FollowedArtistRepository extends JpaRepository<FollowedArtist,Long> {
    boolean existsByHotelAndArtist(User hotel, User artist);

    void deleteByHotelAndArtist(User hotel, User artist);

    long countByArtistId(long artistId);

    List<FollowedArtist> findAllByHotelId(long hotelId);
}
