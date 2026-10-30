package be.cap.backend.accounts;

import java.awt.image.BufferedImage;
import java.io.*;
import java.security.Principal;
import java.time.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

/** Self-service profiles. The owner always comes from the authenticated session. */
@RestController
@Profile("accounts")
@RequestMapping("/api/accounts/profile")
@Transactional(rollbackFor = Exception.class)
public class ProfileController {
    public record Values(String name, String birthDate, String sex, Double heightCm,
                         Double weightKg, String goal, String experience, String phone) {}
    public record Photo(String image) {}
    private final AccountService accounts;
    private final JdbcTemplate jdbc;
    private final MeasurementService measurements;
    private final JsonMapper mapper = JsonMapper.builder().build();

    public ProfileController(AccountService accounts, JdbcTemplate jdbc,MeasurementService measurements) {
        this.accounts = accounts; this.jdbc = jdbc;this.measurements=measurements;
    }
    private ResponseStatusException invalid(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
    private String option(String value, Set<String> choices) {
        String result = value == null ? "" : value;
        if (!choices.contains(result)) throw invalid("Controleer je profielgegevens.");
        return result;
    }
    private Double number(Double value, double min, double max) {
        if (value != null && (!Double.isFinite(value) || value < min || value > max))
            throw invalid("Controleer lengte en gewicht.");
        return value;
    }
    private void ensure(AccountService.Account user) throws IOException {
        jdbc.query("SELECT id FROM cap_user WHERE id=? FOR UPDATE", (row, index) -> row.getObject(1), user.id());
        if (jdbc.queryForObject("SELECT COUNT(*) FROM account_profile WHERE user_id=?", Long.class, user.id()) == 0)
            jdbc.update("INSERT INTO account_profile(user_id,values_json) VALUES(?,?)", user.id(),
                mapper.writeValueAsString(new Values(user.name(), "", "", null, null, "", "", "")));
    }
    @GetMapping
    public Map<String, Object> get(Principal principal) throws IOException {
        var user = accounts.account(principal.getName());
        var rows = jdbc.query("SELECT values_json,photo FROM account_profile WHERE user_id=?",
            (row, index) -> Map.of("json", row.getString(1), "hasPhoto", row.getBytes(2) != null), user.id());
        Values values = rows.isEmpty() ? new Values(user.name(), "", "", null, null, "", "", "")
            : mapper.readValue((String) rows.getFirst().get("json"), Values.class);
        return Map.of("values", values, "email", user.email(), "hasPhoto", !rows.isEmpty() && rows.getFirst().get("hasPhoto").equals(true),
            "measurements",measurements.history(user.id(),measurements.profileMetrics(values.heightCm(),values.weightKg())));
    }
    @PutMapping
    public Map<String, Object> save(@RequestBody Values input, Principal principal) throws IOException {
        var user = accounts.account(principal.getName());
        String name = input.name() == null ? "" : input.name().trim();
        if (name.isBlank() || name.length() > 100) throw invalid("Vul een naam van maximaal 100 tekens in.");
        String birthDate = input.birthDate() == null ? "" : input.birthDate();
        String phone=input.phone()==null ? "" : input.phone().trim();
        if(!phone.isEmpty() && !phone.matches("\\+?[0-9][0-9(). -]{2,38}")) throw invalid("Vul een geldig telefoonnummer van maximaal 40 tekens in.");
        if (!birthDate.isEmpty()) {
            try {
                var date = LocalDate.parse(birthDate);
                if (date.isBefore(LocalDate.of(1900, 1, 1)) || date.isAfter(LocalDate.now(ZoneId.of("Europe/Brussels"))))
                    throw invalid("Controleer je geboortedatum.");
            } catch (java.time.format.DateTimeParseException error) { throw invalid("Controleer je geboortedatum."); }
        }
        var values = new Values(name, birthDate, option(input.sex(), Set.of("", "female", "male", "other")),
            number(input.heightCm(), 50, 300), number(input.weightKg(), 20, 500),
            option(input.goal(), Set.of("", "strength", "fitness", "weight-loss", "health")),
            option(input.experience(), Set.of("", "beginner", "intermediate", "advanced")),phone);
        ensure(user);
        var previous=mapper.readValue(jdbc.queryForObject("SELECT values_json FROM account_profile WHERE user_id=?",String.class,user.id()),Values.class);
        if(!Objects.equals(previous.heightCm(),values.heightCm()) || !Objects.equals(previous.weightKg(),values.weightKg()))
            measurements.recordProfile(user.id(),measurements.profileMetrics(values.heightCm(),values.weightKg()));
        jdbc.update("UPDATE cap_user SET display_name=? WHERE id=?", name, user.id());
        jdbc.update("UPDATE account_profile SET values_json=? WHERE user_id=?", mapper.writeValueAsString(values), user.id());
        return get(principal);
    }
    @GetMapping("/photo")
    public ResponseEntity<byte[]> photo(Principal principal) {
        var user = accounts.account(principal.getName());
        var rows = jdbc.query("SELECT photo FROM account_profile WHERE user_id=?", (row, index) -> row.getBytes(1), user.id());
        if (rows.isEmpty() || rows.getFirst() == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).cacheControl(CacheControl.noStore())
            .header("X-Content-Type-Options", "nosniff").body(rows.getFirst());
    }
    @PutMapping("/photo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void upload(@RequestBody Photo input, Principal principal) throws IOException {
        if (input.image() == null || input.image().length() > 180000 || !input.image().startsWith("data:image/jpeg;base64,"))
            throw invalid("Kies een geldige profielfoto.");
        byte[] bytes;
        try { bytes = Base64.getDecoder().decode(input.image().substring(23)); }
        catch (IllegalArgumentException error) { throw invalid("Kies een geldige profielfoto."); }
        // Check dimensions before decoding; re-encode to discard metadata and extra data.
        try (var stream = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) throw invalid("Kies een geldige profielfoto.");
            var reader = readers.next();
            try {
                reader.setInput(stream);
                if (reader.getWidth(0) > 512 || reader.getHeight(0) > 512) throw invalid("De profielfoto is te groot.");
                BufferedImage image = reader.read(0);
                var output = new ByteArrayOutputStream();
                if (!ImageIO.write(image, "jpg", output)) throw invalid("Kies een JPEG-foto.");
                var user = accounts.account(principal.getName()); ensure(user);
                jdbc.update("UPDATE account_profile SET photo=? WHERE user_id=?", output.toByteArray(), user.id());
            } finally { reader.dispose(); }
        } catch (javax.imageio.IIOException error) { throw invalid("De foto kan niet worden gelezen. Kies een andere foto."); }
    }
    @DeleteMapping("/photo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removePhoto(Principal principal) {
        jdbc.update("UPDATE account_profile SET photo=NULL WHERE user_id=?", accounts.account(principal.getName()).id());
    }
}
