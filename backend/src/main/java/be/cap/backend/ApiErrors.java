package be.cap.backend;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiErrors {
    private static final Logger LOG = LoggerFactory.getLogger(ApiErrors.class);
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> invalid(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).body(Map.of("message", error.getReason() == null ? "Ongeldige aanvraag." : error.getReason()));
    }
    @ExceptionHandler(IOException.class)
    public ResponseEntity<Map<String, String>> storage(IOException error) {
        return unavailable(error);
    }
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, String>> malformed(Exception error) {
        return ResponseEntity.badRequest().body(Map.of("message", "De aanvraag bevat ongeldige gegevens. Controleer de invoer."));
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> unavailable(Exception error) {
        if (error instanceof org.springframework.web.ErrorResponse response)
            return ResponseEntity.status(response.getStatusCode()).body(Map.of("message", "Deze aanvraag of pagina is niet beschikbaar."));
        String reference = UUID.randomUUID().toString().substring(0, 8);
        // Do not log bodies, query parameters, exception messages or SQL bind values.
        LOG.error("CAP request failed: reference={} category={}", reference, error.getClass().getSimpleName());
        return ResponseEntity.internalServerError().body(Map.of("message",
            "CAP kan deze aanvraag tijdelijk niet verwerken. Probeer opnieuw. Referentie: " + reference,
            "reference", reference));
    }
}
