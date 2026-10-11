package app.data;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record LoanReviewRequest(
        @NotNull(message = "Decision is required")
        Decision decision,
        @Size(max = 1000, message = "Reason must be at most 1000 characters")
        String reason
) {
    public enum Decision { APPROVE, REJECT }
}
