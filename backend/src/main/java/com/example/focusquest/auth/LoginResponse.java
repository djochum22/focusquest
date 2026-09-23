package com.example.focusquest.auth;

import com.example.focusquest.user.UserDto;

public record LoginResponse(
        String token,
        String tokenType,
        long expiresInSeconds,
        UserDto user
) {
}
