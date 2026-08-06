package org.example.mypaintings.user;

import jakarta.persistence.*;
import lombok.*;
import org.example.mypaintings.exception.access.MissingRequiredRoleException;
import org.example.mypaintings.exception.validation.BusinessValidationException;

import java.util.regex.Pattern;

@Getter
@Entity
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "app_users")
@EqualsAndHashCode(of = "id")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    @Setter(AccessLevel.PRIVATE)
    private String nickname;

    @Version
    private Long version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    @Setter(AccessLevel.PRIVATE)
    private UserRole role;

    @Column(nullable = false)
    @Setter(AccessLevel.PRIVATE)
    private String fullAddress;

    @Embedded
    private Coordinates coordinates;

    public void changeAddress(String newAddress, double latitude, double longitude) {
        if (this.role == UserRole.HOTEL && this.fullAddress != null && this.getCoordinates() != null) {
            throw new IllegalStateException("Hotel cannot change address");
        }
        addAddressAndCoordinates(newAddress, latitude, longitude);
    }

    private static final Pattern RESERVED_NICKNAME_PATTERN = Pattern.compile(
            "^(admin|administrator|mod|moderator|support|system|root).*$",
            Pattern.CASE_INSENSITIVE
    );

    public void changeNickname(String newNickname) {
        if (newNickname == null || newNickname.isBlank()) {
            throw new BusinessValidationException("Nickname cannot be empty");
        }

        String normalizedNickname = newNickname.trim();

        if (RESERVED_NICKNAME_PATTERN.matcher(normalizedNickname).matches()) {
            throw new BusinessValidationException("This nickname is reserved and cannot be used");
        }

        this.nickname = normalizedNickname;
    }

    public User(UserRole role, String nickname, String fullAddress, double latitude, double longitude) {
        if (role == null) {
            throw new BusinessValidationException("Role cannot be null");
        }
        changeNickname(nickname);
        this.role = role;
        addAddressAndCoordinates(fullAddress, latitude, longitude);
    }

    private void addAddressAndCoordinates(String address, double latitude, double longitude) {
        if (address == null || address.isBlank()) {
            throw new BusinessValidationException("Address cannot be null or empty");
        }
        Coordinates newCoordinates = new Coordinates(latitude, longitude);
        this.fullAddress = address;
        this.coordinates = newCoordinates;
    }

    public void validateRoleOrThrow(UserRole requiredRole) {
        if (this.role != requiredRole) {
            throw new MissingRequiredRoleException(requiredRole);
        }
    }

    public void markCoordinatesAsUnchanged() {
        this.coordinates = this.coordinates.markAsUnchanged();
    }
}