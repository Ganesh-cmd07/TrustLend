package app.controller;

import app.services.AiAssistantService;
import app.data.AiChatRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;

@RestController
@RequestMapping("/api/v1/ai")
public class AiAssistantController {

    private final AiAssistantService aiAssistantService;

    public AiAssistantController(AiAssistantService aiAssistantService) {
        this.aiAssistantService = aiAssistantService;
    }

    @PostMapping("/chat")
    public ResponseEntity<?> chat(@Valid @RequestBody AiChatRequest payload, Authentication authentication) {
        String userQuery = payload.prompt() == null ? "" : payload.prompt();
        return ResponseEntity.ok(aiAssistantService.ask(userQuery, authentication.getName()));
    }

    @GetMapping("/status")
    public ResponseEntity<?> status() {
        return ResponseEntity.ok(aiAssistantService.status());
    }
}
