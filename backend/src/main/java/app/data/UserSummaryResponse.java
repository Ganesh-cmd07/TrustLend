package app.data;

import app.models.User;

public record UserSummaryResponse(Long id, String name, String email, String role) {

    public static UserSummaryResponse from(User user) {
        return new UserSummaryResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole().name()
        );
    }
}
