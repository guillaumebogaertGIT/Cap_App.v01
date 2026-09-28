
package be.cap.backend;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;
import org.springframework.web.bind.annotation.CrossOrigin;


@CrossOrigin(origins = "http://127.0.0.1:5500")
@RestController
public class HealthController {

@GetMapping("/api/health")
public Map<String, String> health() {
    return Map.of("status", "ok", "message", "CAP backend is running");
}
}
