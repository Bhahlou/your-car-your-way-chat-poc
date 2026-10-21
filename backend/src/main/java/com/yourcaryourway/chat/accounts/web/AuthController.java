package com.yourcaryourway.chat.accounts.web;

import com.yourcaryourway.chat.accounts.UserRole;
import com.yourcaryourway.chat.accounts.service.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Controller: validates the input and delegates. No business rule here. */
@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

    private final AuthService authService;

    AuthController(AuthService authService) {
        this.authService = authService;
    }

    record LoginRequest(@NotBlank String email, @NotBlank String password) {
    }

    record UserView(Long id, String displayName, UserRole role) {
    }

    record LoginResponse(String accessToken, long expiresIn, UserView user) {
    }

    @PostMapping("/login")
    LoginResponse login(@Valid @RequestBody LoginRequest request) {
        AuthService.Session session = authService.login(request.email(), request.password());
        var user = session.user();
        return new LoginResponse(session.accessToken(), session.expiresInSeconds(),
            new UserView(user.getId(), user.displayName(), user.getRole()));
    }
}
