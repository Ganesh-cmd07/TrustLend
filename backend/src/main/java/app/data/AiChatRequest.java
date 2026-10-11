package app.data;

import jakarta.validation.constraints.Size;

public record AiChatRequest(
        @Size(max = 2000, message = "Prompt must be at most 2000 characters")
        String prompt
) {
}
