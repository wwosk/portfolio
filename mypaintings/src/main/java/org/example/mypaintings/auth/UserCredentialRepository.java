package org.example.mypaintings.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserCredentialRepository extends JpaRepository<UserCredential, Long> {
    @Query("""
        SELECT c FROM UserCredential c
        JOIN FETCH c.user
        WHERE c.email = :email
    """)
    Optional<UserCredential> findByEmailWithUser(@Param("email") String email);

}