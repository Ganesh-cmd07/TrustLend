package app.data;

import app.models.User;
import java.time.LocalDateTime;

public record AuthenticatedUserResponse(
        Long id,
        String name,
        String email,
        String role,
        String lenderRequestStatus,
        LocalDateTime createdAt
) {

    public static AuthenticatedUserResponse from(User user, String lenderRequestStatus) {
        return new AuthenticatedUserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole().name(),
                lenderRequestStatus,
                user.getCreatedAt()
        );
    }
}
