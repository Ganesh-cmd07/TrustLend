package app.data;

import app.models.LoanApplicationReview;

import java.time.LocalDateTime;

public record LoanReviewResponse(
        Long id,
        Long loanId,
        Long reviewerId,
        String reviewerName,
        String previousStatus,
        String newStatus,
        String reason,
        LocalDateTime createdAt
) {
    public static LoanReviewResponse from(LoanApplicationReview review) {
        return new LoanReviewResponse(
                review.getId(), review.getLoan().getId(), review.getReviewer().getId(),
                review.getReviewer().getName(), review.getPreviousStatus().name(),
                review.getNewStatus().name(), review.getReason(), review.getCreatedAt());
    }
}
