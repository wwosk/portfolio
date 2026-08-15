package org.example.mypaintings.auth;

import org.example.mypaintings.user.User;
import org.example.mypaintings.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@DisplayName("UserCredentialRepository Test Suite")
class UserCredentialRepositoryTest {

    @Autowired
    private UserCredentialRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @BeforeEach
    void setUp() {
        // given
        User user = new User(UserRole.ARTIST, "jankowalski", "ul. Testowa 1, Warszawa", 52.2297, 21.0122);
        UserCredential credential = new UserCredential(user, "jan@example.com", "hashed-password");

        entityManager.persist(user);
        entityManager.persist(credential);
        entityManager.flush();
        entityManager.clear();
    }

    // ------------------------------------------------------------------
    // findByEmailWithUser
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("findByEmailWithUser")
    class FindByEmailWithUser {

        @Test
        @DisplayName("fetches user eagerly when credential exists for email")
        void findByEmailWithUser_fetchesUserEagerly() {
            // when
            Optional<UserCredential> result = repository.findByEmailWithUser("jan@example.com");

            // then
            assertThat(result).isPresent();
            assertThat(result.get().getUser().getNickname()).isEqualTo("jankowalski");
        }

        @Test
        @DisplayName("returns empty optional when email does not exist")
        void findByEmailWithUser_returnsEmpty_whenNotFound() {
            // when
            Optional<UserCredential> result = repository.findByEmailWithUser("brak@example.com");

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty optional when email case differs (if case-sensitive)")
        void findByEmailWithUser_returnsEmpty_whenCaseDiffers() {
            // when
            Optional<UserCredential> result = repository.findByEmailWithUser("JAN@example.com");

            // then
            assertThat(result).isEmpty();
        }
    }
}