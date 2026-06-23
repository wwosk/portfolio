package org.example.mypaintings.user;

import org.example.mypaintings.exception.notfound.ResourceNotFoundException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserRepository extends JpaRepository<User, Long>
{
    boolean existsByNickname(String nickname);

    default User getOrThrow(Long id) {
        return findById(id).orElseThrow(() ->
                new ResourceNotFoundException(User.class,id));
    }

    @Query("SELECT u FROM User u WHERE u.role = 'ARTIST' AND " +
            "u.coordinates.latitude BETWEEN :minLat AND :maxLat AND " +
            "u.coordinates.longitude BETWEEN :minLon AND :maxLon")
    List<User> findArtistsInArea(
            @Param("minLat") double minLat,
            @Param("maxLat") double maxLat,
            @Param("minLon") double minLon,
            @Param("maxLon") double maxLon
    );

    @Query("SELECT u FROM User u WHERE u.role = 'HOTEL' AND u.coordinates.latitude BETWEEN :minLat AND :maxLat AND u.coordinates.longitude BETWEEN :minLon AND :maxLon")
    List<User> findHotelsInArea(@Param("minLat") double minLat,
                                @Param("maxLat") double maxLat,
                                @Param("minLon") double minLon,
                                @Param("maxLon") double maxLon);


    @Query("""
           SELECT u FROM User u
           WHERE u.role = :role
             AND u.coordinates.locationChanged = true
           """)
    List<User> findByRoleAndCoordinatesLocationChangedTrue(@Param("role") UserRole role);


}
