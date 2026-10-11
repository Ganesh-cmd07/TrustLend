package app.data;

import app.models.LenderRequest;

import java.time.LocalDateTime;

public record LenderRequestResponse(
        Long id,
        Long userId,
        String name,
        String email,
        String status,
        LocalDateTime createdAt
) {

    public static LenderRequestResponse from(LenderRequest request) {
        return new LenderRequestResponse(
                request.getId(),
                request.getUser().getId(),
                request.getUser().getName(),
                request.getUser().getEmail(),
                request.getStatus().name(),
                request.getCreatedAt()
        );
    }
}
