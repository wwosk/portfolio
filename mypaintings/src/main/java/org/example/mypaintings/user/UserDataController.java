package org.example.mypaintings.user;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.mypaintings.auth.CurrentUserId;
import org.example.mypaintings.user.dto.UpdateAddressRequest;
import org.example.mypaintings.user.dto.UpdateNicknameRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class UserDataController {

    private final UserDataService userDataService;

    @PatchMapping("/address")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Void> updateAddress(
            @Parameter(hidden = true) @CurrentUserId Long userId,
            @RequestBody @Valid UpdateAddressRequest addressRequest) {
        userDataService.updateUserAddress(userId,addressRequest.latitude(),addressRequest.longitude(),addressRequest.fullAddress());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/nickname")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Void> updateNickname(
            @Parameter(hidden = true) @CurrentUserId Long userId,
            @RequestBody @Valid UpdateNicknameRequest request) {
        userDataService.updateUserNickname(userId, request.nickname());
        return ResponseEntity.noContent().build();
    }
}