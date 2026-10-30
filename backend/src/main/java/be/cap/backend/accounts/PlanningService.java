package be.cap.backend.accounts;

import java.sql.Date;
import java.sql.Time;
import java.time.*;
import java.util.*;
import be.cap.backend.Workout;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Profile("accounts")
public class PlanningService {
    public record Person(UUID id, String name, String role) {}
    public record Input(String title, String date, String start, String end, UUID coachId,
                        String location, int capacity, String type, String category, String program,
                        List<UUID> athleteIds, int repeat) {}
    public record Session(UUID id, String title, LocalDate date, LocalTime start, LocalTime end,
                          UUID coachId, String coach, String location, int capacity, String type,
                          String category, String program, List<UUID> athleteIds, int bookedCount, boolean booked, Workout workoutSnapshot) {}
    public record Booking(UUID sessionId, UUID athleteId, String athlete, boolean cancelled, String attendance) {}
    private final JdbcTemplate jdbc;
    private final be.cap.backend.accounts.billing.LessonAccess lessonAccess;
    private static final ZoneId CLUB_ZONE = ZoneId.of("Europe/Brussels");
    public PlanningService(JdbcTemplate jdbc, be.cap.backend.accounts.billing.LessonAccess lessonAccess) { this.jdbc = jdbc; this.lessonAccess = lessonAccess; }
    private ResponseStatusException invalid(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private void coach(AccountService.Account user) {
        new StaffPolicy(jdbc).require(user,StaffPolicy.Permission.CLASS_MANAGE);
    }
    private boolean visible(UUID id, AccountService.Account user) {
        if (user.role().equals("COACH")) return true;
        return jdbc.queryForObject("SELECT COUNT(*) FROM club_session s WHERE s.id = ? AND (s.session_type <> 'Privé' OR EXISTS (SELECT 1 FROM session_recipient r WHERE r.session_id = s.id AND r.athlete_id = ?))", Long.class, id, user.id()) > 0;
    }
    private Session require(UUID id, AccountService.Account user, boolean lock) {
        if (lock) jdbc.query("SELECT id FROM club_session WHERE id = ? FOR UPDATE", (row, index) -> row.getObject(1), id);
        if (!visible(id, user)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Les niet gevonden.");
        return list(user, null, null).stream().filter(session -> session.id().equals(id)).findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Les niet gevonden."));
    }
    public List<Person> people(AccountService.Account user) {
        var policy=new StaffPolicy(jdbc);policy.require(user,StaffPolicy.Permission.CLASS_VIEW);
        boolean manager=policy.allows(user,StaffPolicy.Permission.CLASS_MANAGE);var clients=manager?Set.<UUID>of():policy.scopedClients(user);
        return jdbc.query("SELECT id, display_name, role FROM cap_user WHERE active=TRUE AND (role='ATHLETE' OR staff_role IN ('OWNER','MANAGER','INSTRUCTOR')) ORDER BY display_name", (row, index) ->
            new Person(row.getObject("id", UUID.class), row.getString("display_name"), row.getString("role"))).stream()
            .filter(p->manager || p.id().equals(user.id()) || p.role().equals("ATHLETE") && clients.contains(p.id())).toList();
    }
    @Transactional(readOnly = true)
    public List<Session> list(AccountService.Account user, LocalDate from, LocalDate through) {
        var policy=new StaffPolicy(jdbc);
        if(user.role().equals("COACH"))policy.require(user,StaffPolicy.Permission.CLASS_VIEW);
        String sql = """
            SELECT s.*, u.display_name AS coach_name,
                (SELECT COUNT(*) FROM session_booking b WHERE b.session_id = s.id AND b.cancelled = FALSE) AS booked_count,
                (SELECT COUNT(*) FROM session_booking b WHERE b.session_id = s.id AND b.athlete_id = ? AND b.cancelled = FALSE) AS mine
            FROM club_session s JOIN cap_user u ON u.id = s.coach_id
            WHERE (? = TRUE OR s.session_type <> 'Privé' OR EXISTS
                (SELECT 1 FROM session_recipient r WHERE r.session_id = s.id AND r.athlete_id = ?))
            """;
        List<Object> parameters = new ArrayList<>(List.of(user.id(), user.role().equals("COACH"), user.id()));
        if(policy.role(user).equals("INSTRUCTOR")){sql+=" AND s.coach_id=?";parameters.add(user.id());}
        if (from != null) { sql += " AND s.session_date >= ?"; parameters.add(Date.valueOf(from)); }
        if (through != null) { sql += " AND s.session_date <= ?"; parameters.add(Date.valueOf(through)); }
        sql += " ORDER BY s.session_date, s.start_time, s.title";
        return jdbc.query(sql, (row, index) -> {
            UUID id = row.getObject("id", UUID.class);
            // Athletes receive no other athlete identities, even in their own private session.
            List<UUID> recipients = policy.allows(user,StaffPolicy.Permission.CLASS_MANAGE)
                ? jdbc.query("SELECT athlete_id FROM session_recipient WHERE session_id = ?", (r, i) -> r.getObject(1, UUID.class), id) : List.of();
            return new Session(id, row.getString("title"), row.getDate("session_date").toLocalDate(),
                row.getTime("start_time").toLocalTime(), row.getTime("end_time").toLocalTime(),
                row.getObject("coach_id", UUID.class), row.getString("coach_name"), row.getString("location"),
                row.getInt("capacity"), row.getString("session_type"), row.getString("category"), row.getString("program"),
                recipients, row.getInt("booked_count"), row.getInt("mine") > 0,
                row.getString("workout_snapshot") == null ? null : JsonMapper.builder().build().readValue(row.getString("workout_snapshot"),Workout.class));
        }, parameters.toArray());
    }
    private void upcoming(Session session) {
        if (!LocalDateTime.of(session.date(), session.start()).isAfter(LocalDateTime.now(CLUB_ZONE)))
            throw invalid("Deze les is al gestart. Kies een toekomstige les.");
    }
    @Transactional
    public List<Session> save(UUID id, Input input, AccountService.Account user) {
        coach(user);
        Session old = id == null ? null : require(id, user, true);
        if (old != null) upcoming(old);
        if (input == null || input.title() == null || input.title().isBlank() || input.title().length() > 100
            || input.location() == null || input.location().isBlank() || input.location().length() > 120
            || input.capacity() < 1 || input.capacity() > 200 || input.coachId() == null
            || input.type() == null || !List.of("Groep", "Privé", "Open gym").contains(input.type())
            || input.program() != null && input.program().length() > 3000) throw invalid("Controleer naam, locatie, coach en capaciteit.");
        LocalDate date; LocalTime start; LocalTime end;
        try { date = LocalDate.parse(input.date()); start = LocalTime.parse(input.start()); end = LocalTime.parse(input.end()); }
        catch (RuntimeException e) { throw invalid("Controleer datum en tijden."); }
        if (!end.isAfter(start) || !LocalDateTime.of(date, start).isAfter(LocalDateTime.now(CLUB_ZONE))) throw invalid("Kies een toekomstige datum en een eindtijd na de starttijd.");
        String category = input.type().equals("Groep") ? Objects.requireNonNullElse(input.category(), "") : "";
        if (!List.of("", "CAP HYROX", "CAP Hybrid", "CAP Move", "CAP Lift & HIT").contains(category)) throw invalid("Kies een geldige CAP groep.");
        var people = people(user);
        if (people.stream().noneMatch(person -> person.id().equals(input.coachId()) && person.role().equals("COACH"))) throw invalid("Kies een coach uit de club.");
        List<UUID> recipients = input.type().equals("Privé") && input.athleteIds() != null ? input.athleteIds().stream().distinct().toList() : List.of();
        if (input.type().equals("Privé") && (recipients.isEmpty() || recipients.size() > input.capacity()
            || recipients.stream().anyMatch(recipient -> people.stream().noneMatch(person -> person.id().equals(recipient) && person.role().equals("ATHLETE")))))
            throw invalid("Selecteer sporters en voorzie voor iedereen een plaats.");
        int repeat = id == null ? input.repeat() : 1;
        if (!List.of(1, 4, 8).contains(repeat)) throw invalid("Kies eenmalig, 4 of 8 weken.");
        if (old != null && !input.type().equals("Privé") && old.bookedCount() > input.capacity()) throw invalid("De capaciteit kan niet lager zijn dan het aantal boekingen.");
        List<UUID> saved = new ArrayList<>();
        for (int index = 0; index < repeat; index++) {
            UUID sessionId = id == null ? UUID.randomUUID() : id;
            Object[] fields = { input.title().trim(), Date.valueOf(date.plusWeeks(index)), Time.valueOf(start), Time.valueOf(end), input.coachId(), input.location().trim(), input.capacity(), input.type(), category,
                input.type().equals("Open gym") ? "" : Objects.requireNonNullElse(input.program(), "").trim(), sessionId };
            if (id == null) jdbc.update("INSERT INTO club_session (title, session_date, start_time, end_time, coach_id, location, capacity, session_type, category, program, id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", fields);
            else jdbc.update("UPDATE club_session SET title=?, session_date=?, start_time=?, end_time=?, coach_id=?, location=?, capacity=?, session_type=?, category=?, program=? WHERE id=?", fields);
            if (old != null && input.type().equals("Privé")) lessonAccess.releaseSession(sessionId);
            if (input.type().equals("Open gym") || old != null && !Objects.equals(old.program(),Objects.requireNonNullElse(input.program(),"").trim()))
                jdbc.update("UPDATE club_session SET workout_snapshot=NULL WHERE id=?",sessionId);
            jdbc.update("DELETE FROM session_recipient WHERE session_id = ?", sessionId);
            if (input.type().equals("Privé")) {
                // Remove old recipients/legacy bookings before adding the chosen private attendees.
                var existing = jdbc.query("SELECT athlete_id FROM session_booking WHERE session_id = ?", (row, i) -> row.getObject(1, UUID.class), sessionId);
                existing.stream().filter(athlete -> !recipients.contains(athlete)).forEach(athlete -> jdbc.update("DELETE FROM session_booking WHERE session_id = ? AND athlete_id = ?", sessionId, athlete));
                recipients.forEach(athlete -> {
                    jdbc.update("INSERT INTO session_recipient (session_id, athlete_id) VALUES (?, ?)", sessionId, athlete);
                    if (!existing.contains(athlete)) jdbc.update("INSERT INTO session_booking (session_id, athlete_id) VALUES (?, ?)", sessionId, athlete);
                });
            }
            saved.add(sessionId);
        }
        return list(user, date, date.plusWeeks(repeat - 1)).stream().filter(session -> saved.contains(session.id())).toList();
    }
    @Transactional
    public void delete(UUID id, AccountService.Account user) {
        coach(user); upcoming(require(id, user, true));
        lessonAccess.releaseSession(id);
        jdbc.update("DELETE FROM club_session WHERE id = ?", id);
    }
    @Transactional
    public void book(UUID id, AccountService.Account user, boolean cancel) {
        if (!user.role().equals("ATHLETE")) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        Session session = require(id, user, true);
        upcoming(session);
        if (!cancel && !session.booked() && session.bookedCount() >= session.capacity()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Deze les is volzet.");
        if (cancel) lessonAccess.release(user.id(),id);
        else if (!session.booked() && List.of("Groep", "Open gym").contains(session.type()))
            lessonAccess.reserve(user.id(),id,LocalDateTime.of(session.date(),session.start()).atZone(CLUB_ZONE).toInstant());
        int updated = jdbc.update("UPDATE session_booking SET cancelled = ? WHERE session_id = ? AND athlete_id = ?", cancel, id, user.id());
        if (updated == 0 && !cancel) jdbc.update("INSERT INTO session_booking (session_id, athlete_id) VALUES (?, ?)", id, user.id());
    }
    @Transactional(readOnly = true)
    public List<Booking> bookings(AccountService.Account user) {
        var policy=new StaffPolicy(jdbc);
        if(user.role().equals("COACH")) {
            policy.require(user,StaffPolicy.Permission.CLASS_VIEW);
            if(!policy.allows(user,StaffPolicy.Permission.CLASS_MANAGE))return List.of();
        }
        return jdbc.query("""
            SELECT b.*, u.display_name FROM session_booking b JOIN cap_user u ON u.id = b.athlete_id
            JOIN club_session s ON s.id = b.session_id
            WHERE (? = TRUE OR (b.athlete_id = ? AND (s.session_type <> 'Privé' OR EXISTS
                (SELECT 1 FROM session_recipient r WHERE r.session_id = s.id AND r.athlete_id = ?))))
            ORDER BY s.session_date, s.start_time, u.display_name
            """, (row, index) -> new Booking(row.getObject("session_id", UUID.class), row.getObject("athlete_id", UUID.class),
                row.getString("display_name"), row.getBoolean("cancelled"), row.getString("attendance")), user.role().equals("COACH"), user.id(), user.id());
    }
    @Transactional
    public void attendance(UUID sessionId, UUID athleteId, String status, AccountService.Account user) {
        coach(user); require(sessionId, user, true);
        if (status == null || !List.of("Onbekend", "Aanwezig", "Afwezig").contains(status)) throw invalid("Kies een geldige aanwezigheidsstatus.");
        if (jdbc.update("UPDATE session_booking SET attendance = ? WHERE session_id = ? AND athlete_id = ? AND cancelled = FALSE", status, sessionId, athleteId) != 1)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Boeking niet gevonden.");
    }
    @Transactional
    public void cancelBooking(UUID sessionId,UUID athleteId,AccountService.Account user) {
        coach(user);upcoming(require(sessionId,user,true));
        lessonAccess.release(athleteId,sessionId);
        if(jdbc.update("UPDATE session_booking SET cancelled=TRUE WHERE session_id=? AND athlete_id=?",sessionId,athleteId)!=1)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Boeking niet gevonden.");
    }
}
