package com.example.focusquest.user;

import java.time.Instant;

public record UserDto(
        Long id,
        String username,
        String displayName,
        String timezone,
        Instant createdAt
) {

    public static UserDto from(User user) {
        return new UserDto(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getTimezone(),
                user.getCreatedAt()
        );
    }
}
