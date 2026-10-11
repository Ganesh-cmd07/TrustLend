package app.data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GroupCreateRequest(
        @NotBlank(message = "Group name is required")
        @Size(max = 100, message = "Group name cannot exceed 100 characters")
        String name,

        @Size(max = 1000, message = "Description cannot exceed 1000 characters")
        String description
) {
}
