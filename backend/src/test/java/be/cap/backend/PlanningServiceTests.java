package be.cap.backend;

import be.cap.backend.accounts.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("accounts")
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:planning;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "cap.accounts.bootstrap-owner=true", "cap.accounts.bootstrap-email=planning-coach@example.test", "cap.accounts.bootstrap-password=Coach-test-password-123"
})
class PlanningServiceTests {
    @org.springframework.test.context.DynamicPropertySource
    static void postgres(org.springframework.test.context.DynamicPropertyRegistry properties) { PostgresTestDatabase.configure(properties,"planning"); }
    @Autowired AccountService accounts;
    @Autowired PlanningService planning;
    AccountService.Account coach() { return accounts.account("planning-coach@example.test"); }
    AccountService.Account athlete() {
        String email = UUID.randomUUID() + "@example.test";
        var invitation = accounts.invite(email, coach().email());
        return accounts.accept(invitation.token(), "Test sporter", "Athlete-test-password-123");
    }
    PlanningService.Input input(String type, int capacity, List<UUID> recipients, int repeat) {
        return new PlanningService.Input("Test les", LocalDate.now(ZoneId.of("Europe/Brussels")).plusDays(2).toString(),
            "18:00", "19:00", coach().id(), "CAP Gistel", capacity, type, type.equals("Groep") ? "CAP HYROX" : "", "Test programma", recipients, repeat);
    }
    @Test void privateRecipientsAndBookingsStayPrivateWhenEdited() {
        var first=athlete(); var second=athlete();
        var session=planning.save(null,input("Privé",1,List.of(first.id()),1),coach()).getFirst();
        assertTrue(planning.list(first,null,null).stream().anyMatch(s->s.id().equals(session.id()) && s.booked()));
        assertTrue(planning.list(first,null,null).stream().filter(s->s.id().equals(session.id())).allMatch(s->s.athleteIds().isEmpty()));
        assertFalse(planning.list(second,null,null).stream().anyMatch(s->s.id().equals(session.id())));
        assertThrows(ResponseStatusException.class,()->planning.book(session.id(),second,false));
        planning.save(session.id(),input("Privé",1,List.of(second.id()),1),coach());
        assertFalse(planning.list(first,null,null).stream().anyMatch(s->s.id().equals(session.id())));
        assertFalse(planning.bookings(first).stream().anyMatch(b->b.sessionId().equals(session.id())));
        assertTrue(planning.bookings(second).stream().anyMatch(b->b.sessionId().equals(session.id())));
        assertThrows(ResponseStatusException.class,()->planning.save(null,input("Groep",8,List.of(),1),first));
    }
    @Test void concurrentAthletesCannotBothTakeLastPlaceAndCancellationReleasesIt() throws Exception {
        var first=athlete(); var second=athlete();
        var session=planning.save(null,input("Groep",1,List.of(),1),coach()).getFirst();
        var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            List<Future<Boolean>> results=new ArrayList<>();
            for(var athlete:List.of(first,second)) results.add(pool.submit(()->{
                start.await();
                try { planning.book(session.id(),athlete,false); return true; }
                catch(ResponseStatusException full) { assertEquals(409,full.getStatusCode().value()); return false; }
            }));
            start.countDown();
            int successes=0; for(var result:results) if(result.get(10,TimeUnit.SECONDS)) successes++;
            assertEquals(1,successes);
        }
        var booking=planning.bookings(coach()).stream().filter(b->b.sessionId().equals(session.id())&&!b.cancelled()).findFirst().orElseThrow();
        var winner=booking.athleteId().equals(first.id())?first:second;
        var loser=winner.id().equals(first.id())?second:first;
        planning.book(session.id(),winner,false); // Retry is idempotent.
        planning.book(session.id(),winner,true);
        planning.book(session.id(),loser,false);
        assertEquals(1,planning.list(coach(),null,null).stream().filter(s->s.id().equals(session.id())).findFirst().orElseThrow().bookedCount());
    }
    @Test void repeatAttendanceAndDeletionPersistWithoutInventedAthletes() {
        var athlete=athlete();
        var sessions=planning.save(null,input("Privé",1,List.of(athlete.id()),4),coach());
        assertEquals(4,sessions.size());
        assertEquals(21,java.time.temporal.ChronoUnit.DAYS.between(sessions.getFirst().date(),sessions.getLast().date()));
        UUID id=sessions.getFirst().id();
        planning.attendance(id,athlete.id(),"Aanwezig",coach());
        assertEquals("Aanwezig",planning.bookings(athlete).stream().filter(b->b.sessionId().equals(id)).findFirst().orElseThrow().attendance());
        assertThrows(ResponseStatusException.class,()->planning.attendance(id,athlete.id(),"Aanwezig",athlete));
        planning.delete(id,coach());
        assertFalse(planning.bookings(athlete).stream().anyMatch(b->b.sessionId().equals(id)));
    }
}
