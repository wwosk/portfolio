package org.example.mypaintings.painting;

import jakarta.persistence.LockModeType;

import org.example.mypaintings.exception.notfound.ResourceNotFoundException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;



public interface PaintingRepository extends JpaRepository<Painting, Long>, JpaSpecificationExecutor<Painting> {


    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Painting p WHERE p.id = :id")
    Optional<Painting> findByIdWithLock(@Param("id") Long id);

    @Query("SELECT DISTINCT p FROM Painting p " +
            "LEFT JOIN FETCH p.attachments " +
            "LEFT JOIN FETCH p.artist " +
            "LEFT JOIN FETCH p.styles " +
            "WHERE p.id = :id")
    Optional<Painting> findByIdWithDetails(@Param("id") long id);

    default Painting getWithDetailsOrThrow(long id) {
        return findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException(Painting.class, id));
    }


    default Painting getOrThrow(Long id) {
        return findById(id).orElseThrow(() ->
                new ResourceNotFoundException(Painting.class,id));
    }


}
