package org.example.mypaintings.user;

import org.example.mypaintings.exception.access.MissingRequiredRoleException;
import org.example.mypaintings.exception.validation.BusinessValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserTest {

    private static final String VALID_NICKNAME = "vanGogh";
    private static final String VALID_ADDRESS = "Museum Street 1, Amsterdam";
    private static final double VALID_LATITUDE = 52.3676;
    private static final double VALID_LONGITUDE = 4.9041;

    // ---------------------------------------------------------------------
    // Business constructor
    // ---------------------------------------------------------------------

    @ParameterizedTest(name = "Business constructor: creates user successfully for role \"{0}\"")
    @EnumSource(UserRole.class)
    @DisplayName("Business constructor: creates user for all valid enum roles")
    void businessConstructor_shouldCreateUser_forAllValidRoles(UserRole role) {
        // when
        User user = new User(role, VALID_NICKNAME, VALID_ADDRESS, VALID_LATITUDE, VALID_LONGITUDE);

        // then
        assertThat(user.getRole()).isEqualTo(role);
        assertThat(user.getNickname()).isEqualTo(VALID_NICKNAME);
    }


    @Test
    @DisplayName("Business constructor: creates user when all data is valid")
    void businessConstructor_shouldCreateUser_whenAllDataValid() {
        // given
        UserRole role = UserRole.ARTIST;

        // when
        User user = new User(role, VALID_NICKNAME, VALID_ADDRESS, VALID_LATITUDE, VALID_LONGITUDE);

        // then
        assertThat(user.getRole()).isEqualTo(role);
        assertThat(user.getNickname()).isEqualTo(VALID_NICKNAME);
        assertThat(user.getFullAddress()).isEqualTo(VALID_ADDRESS);
        assertThat(user.getCoordinates()).isNotNull();
        assertThat(user.getCoordinates().getLatitude()).isEqualTo(VALID_LATITUDE);
        assertThat(user.getCoordinates().getLongitude()).isEqualTo(VALID_LONGITUDE);
        assertThat(user.getCoordinates().isLocationChanged()).isTrue();
    }

    @Test
    @DisplayName("Business constructor: trims whitespace from nickname")
    void businessConstructor_shouldTrimNickname_whenNicknameHasWhitespace() {
        // given
        String paddedNickname = "   monet   ";

        // when
        User user = new User(UserRole.ARTIST, paddedNickname, VALID_ADDRESS, VALID_LATITUDE, VALID_LONGITUDE);

        // then
        assertThat(user.getNickname()).isEqualTo("monet");
    }

    @Test
    @DisplayName("Business constructor: throws when nickname is null")
    void businessConstructor_shouldThrow_whenNicknameIsNull() {
        // given
        String nullNickname = null;

        // when / then
        assertThatThrownBy(() -> new User(UserRole.ARTIST, nullNickname, VALID_ADDRESS, VALID_LATITUDE, VALID_LONGITUDE))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessage("Nickname cannot be empty");
    }

    @Test
    @DisplayName("Business constructor: throws when nickname is blank")
    void businessConstructor_shouldThrow_whenNicknameIsBlank() {
        // given
        String blankNickname = "   ";

        // when / then
        assertThatThrownBy(() -> new User(UserRole.ARTIST, blankNickname, VALID_ADDRESS, VALID_LATITUDE, VALID_LONGITUDE))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessage("Nickname cannot be empty");
    }

    @Test
    @DisplayName("Business constructor: throws when address is null")
    void businessConstructor_shouldThrow_whenAddressIsNull() {
        // given
        String nullAddress = null;

        // when / then
        assertThatThrownBy(() -> new User(UserRole.ARTIST, VALID_NICKNAME, nullAddress, VALID_LATITUDE, VALID_LONGITUDE))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessage("Address cannot be null or empty");
    }

    @Test
    @DisplayName("Business constructor: throws when address is blank")
    void businessConstructor_shouldThrow_whenAddressIsBlank() {
        // given
        String blankAddress = "   ";

        // when / then
        assertThatThrownBy(() -> new User(UserRole.ARTIST, VALID_NICKNAME, blankAddress, VALID_LATITUDE, VALID_LONGITUDE))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessage("Address cannot be null or empty");
    }

    @Test
    @DisplayName("Business constructor: throws when coordinates are out of range")
    void businessConstructor_shouldThrow_whenCoordinatesOutOfRange() {
        // given
        double invalidLatitude = 120.0;

        // when / then
        assertThatThrownBy(() -> new User(UserRole.ARTIST, VALID_NICKNAME, VALID_ADDRESS, invalidLatitude, VALID_LONGITUDE))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessage("Invalid geographic coordinates");
    }

    @Test
    @DisplayName("Business constructor: throws when role is null")
    void businessConstructor_shouldThrow_whenRoleIsNull() {
        // given
        UserRole nullRole = null;

        // when / then
        assertThatThrownBy(() -> new User(nullRole, VALID_NICKNAME, VALID_ADDRESS, VALID_LATITUDE, VALID_LONGITUDE))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessage("Role cannot be null");
    }

    // ---------------------------------------------------------------------
    // changeNickname
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("changeNickname: changes nickname when valid")
    void changeNickname_shouldChangeNickname_whenValid() {
        // given
        User user = new User(UserRole.ARTIST, VALID_NICKNAME, VALID_ADDRESS, VALID_LATITUDE, VALID_LONGITUDE);

        // when
        user.changeNickname("picasso");

        // then
        assertThat(user.getNickname()).isEqualTo("picasso");
    }

    @Test
    @DisplayName("changeNickname: throws when nickname is null")
    void changeNickname_shouldThrow_whenNicknameIsNull() {
        // given
        User user = new User(UserRole.ARTIST, VALID_NICKNAME, VALID_ADDRESS, VALID_LATITUDE, VALID_LONGITUDE);

        // when / then
        assertThatThrownBy(() -> user.changeNickname(null))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessage("Nickname cannot be empty");
    }

    @Test
    @DisplayName("changeNickname: throws when nickname is blank")
    void changeNickname_shouldThrow_whenNicknameIsBlank() {
        // given
        User user = new User(UserRole.ARTIST, VALID_NICKNAME, VALID_ADDRESS, VALID_LATITUDE, VALID_LONGITUDE);

        // when / then
        assertThatThrownBy(() -> user.changeNickname("   "))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessage("Nickname cannot be empty");
    }

    @ParameterizedTest(name = "changeNickname: rejects reserved nickname \"{0}\"")
    @ValueSource(strings = {
            "admin",
            "administrator",
            "ADMINISTRATOR",
            "Admin",
            "mod",
            "moderator",
            "support",
            "system",
            "root",
            "root123",
            "rootUser",
            "supportTeam"
    })
    @DisplayName("changeNickname: throws for reserved nicknames (case-insensitive, prefix-aware)")
    void changeNickname_shouldThrow_whenNicknameIsReserved(String reservedNickname) {
        // given
        User user = new User(UserRole.ARTIST, VALID_NICKNAME, VALID_ADDRESS, VALID_LATITUDE, VALID_LONGITUDE);

        // when / then
        assertThatThrownBy(() -> user.changeNickname(reservedNickname))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessage("This nickname is reserved and cannot be used");
    }

    @ParameterizedTest(name = "changeNickname: accepts non-reserved nickname \"{0}\"")
    @ValueSource(strings = {
            "VanGogh",
            "picasso",
            "monet",
            "myAdmin",
            "superRoot",
            "notAModerator",
            "adm",
            "roo"
    })
    @DisplayName("changeNickname: accepts nicknames that don't match the reserved pattern")
    void changeNickname_shouldAccept_whenNicknameIsNotReserved(String nickname) {
        // given
        User user = new User(UserRole.ARTIST, VALID_NICKNAME, VALID_ADDRESS, VALID_LATITUDE, VALID_LONGITUDE);

        // when
        user.changeNickname(nickname);

        // then
        assertThat(user.getNickname()).isEqualTo(nickname);
    }

    @Test
    @DisplayName("changeNickname: trims whitespace when changing")
    void changeNickname_shouldTrim_whenWhitespaceProvided() {
        // given
        User user = new User(UserRole.ARTIST, VALID_NICKNAME, VALID_ADDRESS, VALID_LATITUDE, VALID_LONGITUDE);

        // when
        user.changeNickname("  rembrandt  ");

        // then
        assertThat(user.getNickname()).isEqualTo("rembrandt");
    }

    @Test
    @DisplayName("changeNickname: does not change nickname when new value is invalid (atomicity)")
    void changeNickname_shouldNotChangeNickname_whenNewNicknameIsInvalid() {
        // given
        User user = new User(UserRole.ARTIST, VALID_NICKNAME, VALID_ADDRESS, VALID_LATITUDE, VALID_LONGITUDE);

        // when
        assertThatThrownBy(() -> user.changeNickname("admin"))
                .isInstanceOf(BusinessValidationException.class);

        // then
        assertThat(user.getNickname()).isEqualTo(VALID_NICKNAME);
    }

    // ---------------------------------------------------------------------
    // changeAddress
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("changeAddress: ARTIST can change address")
    void changeAddress_shouldUpdateAddress_whenArtist() {
        // given
        User artist = new User(UserRole.ARTIST, VALID_NICKNAME, VALID_ADDRESS, VALID_LATITUDE, VALID_LONGITUDE);
        String newAddress = "New Street 2, Paris";
        double newLatitude = 48.8566;
        double newLongitude = 2.3522;

        // when
        artist.changeAddress(newAddress, newLatitude, newLongitude);

        // then
        assertThat(artist.getFullAddress()).isEqualTo(newAddress);
        assertThat(artist.getCoordinates().getLatitude()).isEqualTo(newLatitude);
        assertThat(artist.getCoordinates().getLongitude()).isEqualTo(newLongitude);
        assertThat(artist.getCoordinates().isLocationChanged()).isTrue();
    }

    @Test
    @DisplayName("changeAddress: HOTEL cannot change address (created with address, immutable afterwards)")
    void changeAddress_shouldThrow_whenHotelTriesToChangeAddress() {
        // given — HOTEL is always created with an address so that it has an ID on save
        User hotel = new User(UserRole.HOTEL, "grandHotel", VALID_ADDRESS, VALID_LATITUDE, VALID_LONGITUDE);

        // when / then
        assertThatThrownBy(() -> hotel.changeAddress("Another Street 5", 50.0, 20.0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Hotel cannot change address");
    }

    @Test
    @DisplayName("changeAddress: HOTEL cannot set the same address again")
    void changeAddress_shouldThrow_whenHotelSetsSameAddress() {
        // given
        User hotel = new User(UserRole.HOTEL, "grandHotel", VALID_ADDRESS, VALID_LATITUDE, VALID_LONGITUDE);

        // when / then
        assertThatThrownBy(() -> hotel.changeAddress(VALID_ADDRESS, VALID_LATITUDE, VALID_LONGITUDE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Hotel cannot change address");
    }

    @Test
    @DisplayName("changeAddress: throws when new address is null")
    void changeAddress_shouldThrow_whenNewAddressIsNull() {
        // given
        User artist = new User(UserRole.ARTIST, VALID_NICKNAME, VALID_ADDRESS, VALID_LATITUDE, VALID_LONGITUDE);

        // when / then
        assertThatThrownBy(() -> artist.changeAddress(null, 50.0, 20.0))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessage("Address cannot be null or empty");
    }

    @Test
    @DisplayName("changeAddress: throws when new address is blank")
    void changeAddress_shouldThrow_whenNewAddressIsBlank() {
        // given
        User artist = new User(UserRole.ARTIST, VALID_NICKNAME, VALID_ADDRESS, VALID_LATITUDE, VALID_LONGITUDE);

        // when / then
        assertThatThrownBy(() -> artist.changeAddress("   ", 50.0, 20.0))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessage("Address cannot be null or empty");
    }

    @Test
    @DisplayName("changeAddress: throws when coordinates are invalid")
    void changeAddress_shouldThrow_whenCoordinatesInvalid() {
        // given
        User artist = new User(UserRole.ARTIST, VALID_NICKNAME, VALID_ADDRESS, VALID_LATITUDE, VALID_LONGITUDE);

        // when / then
        assertThatThrownBy(() -> artist.changeAddress("New Street 2", 200.0, 20.0))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessage("Invalid geographic coordinates");
    }

    @Test
    @DisplayName("changeAddress: does not modify state when coordinates are invalid (atomicity)")
    void changeAddress_shouldNotModifyState_whenCoordinatesInvalid() {
        // given
        User artist = new User(UserRole.ARTIST, VALID_NICKNAME, VALID_ADDRESS, VALID_LATITUDE, VALID_LONGITUDE);

        // when
        assertThatThrownBy(() -> artist.changeAddress("New Street 2", 200.0, 20.0))
                .isInstanceOf(BusinessValidationException.class);

        // then
        assertThat(artist.getFullAddress()).isEqualTo(VALID_ADDRESS);
        assertThat(artist.getCoordinates().getLatitude()).isEqualTo(VALID_LATITUDE);
        assertThat(artist.getCoordinates().getLongitude()).isEqualTo(VALID_LONGITUDE);
    }

    @Test
    @DisplayName("changeAddress: does not modify state when new address is invalid (atomicity)")
    void changeAddress_shouldNotModifyState_whenNewAddressInvalid() {
        // given
        User artist = new User(UserRole.ARTIST, VALID_NICKNAME, VALID_ADDRESS, VALID_LATITUDE, VALID_LONGITUDE);

        // when
        assertThatThrownBy(() -> artist.changeAddress("   ", 50.0, 20.0))
                .isInstanceOf(BusinessValidationException.class);

        // then
        assertThat(artist.getFullAddress()).isEqualTo(VALID_ADDRESS);
        assertThat(artist.getCoordinates().getLatitude()).isEqualTo(VALID_LATITUDE);
        assertThat(artist.getCoordinates().getLongitude()).isEqualTo(VALID_LONGITUDE);
    }

    // ---------------------------------------------------------------------
    // validateRoleOrThrow
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("validateRoleOrThrow: does not throw when role matches")
    void validateRoleOrThrow_shouldNotThrow_whenRoleMatches() {
        // given
        User artist = new User(UserRole.ARTIST, VALID_NICKNAME, VALID_ADDRESS, VALID_LATITUDE, VALID_LONGITUDE);

        // when / then
        artist.validateRoleOrThrow(UserRole.ARTIST);
    }

    @Test
    @DisplayName("validateRoleOrThrow: throws when role does not match")
    void validateRoleOrThrow_shouldThrow_whenRoleDoesNotMatch() {
        // given
        User artist = new User(UserRole.ARTIST, VALID_NICKNAME, VALID_ADDRESS, VALID_LATITUDE, VALID_LONGITUDE);

        // when / then
        assertThatThrownBy(() -> artist.validateRoleOrThrow(UserRole.HOTEL))
                .isInstanceOf(MissingRequiredRoleException.class)
                .hasMessage("You don't have a needed role: HOTEL");
    }

    // ---------------------------------------------------------------------
    // markCoordinatesAsUnchanged
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("markCoordinatesAsUnchanged: marks coordinates as unchanged")
    void markCoordinatesAsUnchanged_shouldSetLocationChangedToFalse() {
        // given
        User user = new User(UserRole.ARTIST, VALID_NICKNAME, VALID_ADDRESS, VALID_LATITUDE, VALID_LONGITUDE);
        assertThat(user.getCoordinates().isLocationChanged()).isTrue();

        // when
        user.markCoordinatesAsUnchanged();

        // then
        assertThat(user.getCoordinates().isLocationChanged()).isFalse();
        assertThat(user.getCoordinates().getLatitude()).isEqualTo(VALID_LATITUDE);
        assertThat(user.getCoordinates().getLongitude()).isEqualTo(VALID_LONGITUDE);
    }

    @Test
    @DisplayName("markCoordinatesAsUnchanged: is idempotent")
    void markCoordinatesAsUnchanged_shouldBeIdempotent() {
        // given
        User user = new User(UserRole.ARTIST, VALID_NICKNAME, VALID_ADDRESS, VALID_LATITUDE, VALID_LONGITUDE);

        // when
        user.markCoordinatesAsUnchanged();
        user.markCoordinatesAsUnchanged();

        // then
        assertThat(user.getCoordinates().isLocationChanged()).isFalse();
        assertThat(user.getCoordinates().getLatitude()).isEqualTo(VALID_LATITUDE);
        assertThat(user.getCoordinates().getLongitude()).isEqualTo(VALID_LONGITUDE);
    }

    @Test
    @DisplayName("markCoordinatesAsUnchanged: throws NPE when coordinates are null (no-args object)")
    void markCoordinatesAsUnchanged_shouldThrowNpe_whenCoordinatesAreNull() {
        // given
        User emptyUser = new User();
        assertThat(emptyUser.getCoordinates()).isNull();

        // when / then
        assertThatThrownBy(emptyUser::markCoordinatesAsUnchanged)
                .isInstanceOf(NullPointerException.class);
    }



}