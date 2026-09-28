package be.cap.backend;

import java.io.IOException;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> invalid(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).body(Map.of("message", error.getReason() == null ? "Ongeldige aanvraag." : error.getReason()));
    }
    @ExceptionHandler(IOException.class)
    public ResponseEntity<Map<String, String>> storage(IOException error) {
        return ResponseEntity.internalServerError().body(Map.of("message", "Opslaan mislukt. Je invoer blijft behouden; probeer opnieuw."));
    }
}
