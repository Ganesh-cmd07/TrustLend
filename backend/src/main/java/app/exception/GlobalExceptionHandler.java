package app.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationFailure(
            MethodArgumentNotValidException exception) {
        Map<String, String> fieldErrors = new TreeMap<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            String field = fieldError.getField();
            String message = fieldError.getDefaultMessage();
            if (field != null && message != null) {
                fieldErrors.putIfAbsent(field, message);
            }
        }

        return errorResponse(
                HttpStatus.BAD_REQUEST,
                "Validation failed",
                Map.of("fields", fieldErrors));
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> handleApiException(ApiException exception) {
        return errorResponse(
                exception.getStatus(),
                exception.getMessage(),
                Map.of());
    }

    private ResponseEntity<Map<String, Object>> errorResponse(
            HttpStatus status,
            String message,
            Map<String, ?> details) {
        return ResponseEntity.status(status).body(Map.of(
                "timestamp", Instant.now().toString(),
                "status", status.value(),
                "error", status.getReasonPhrase(),
                "message", message,
                "details", details));
    }
}
