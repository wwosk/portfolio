package org.example.mypaintings.geolocation;

import org.example.mypaintings.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;


@Repository
public interface UserProximityRepository extends JpaRepository<UserProximity, Long> {

    @Query("""
        SELECT up FROM UserProximity up
        WHERE up.nearbyUser = :artist
          AND up.user IN :hotels
    """)
    List<UserProximity> findAllByNearbyUserAndUserIn(
            @Param("artist") User artist,
            @Param("hotels") Collection<User> hotels);



    @Query("""
    SELECT up FROM UserProximity up
    WHERE up.user = :hotel
      AND up.nearbyUser IN :artists
""")
    List<UserProximity> findAllByUserAndNearbyUserIn(
            @Param("hotel") User hotel,
            @Param("artists") Collection<User> artists);

}