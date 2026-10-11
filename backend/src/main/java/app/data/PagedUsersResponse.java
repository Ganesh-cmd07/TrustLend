package app.data;

import app.models.User;
import org.springframework.data.domain.Page;

import java.util.List;

public record PagedUsersResponse(List<UserSummaryResponse> content, int page, int size,
                                long totalElements, int totalPages) {
    public static PagedUsersResponse from(Page<User> result) {
        return new PagedUsersResponse(
                result.getContent().stream().map(UserSummaryResponse::from).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }
}
