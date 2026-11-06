package be.cap.backend;

import java.util.*;
import java.net.http.HttpClient;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import static org.junit.jupiter.api.Assertions.*;

@TestPropertySource(properties="spring.datasource.url=jdbc:h2:mem:owner-agenda;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
class OwnerAgendaTests extends AccountApiTests {
    @Test void homeIncludesOverdueTasksAcrossMonthsButNotFutureOrCompletedTasks() throws Exception {
        var admin=client();login(admin,"coach@example.test","Coach-test-password-123");
        UUID owner=jdbc.queryForObject("SELECT id FROM cap_user WHERE email='coach@example.test'",UUID.class);
        var today=java.time.LocalDate.now(java.time.ZoneId.of("Europe/Brussels"));
        for(int i=0;i<4;i++){
            var due=i==0?today.minusMonths(2):i==1?today:today.plusDays(1);
            UUID id=UUID.randomUUID();
            jdbc.update("INSERT INTO owner_task(id,title,notes,due_date,done,created_by) VALUES(?,?,'',?,?,?)",id,"Home fixture "+i,due,i==3,owner);
            jdbc.update("INSERT INTO owner_task_assignee(task_id,user_id) VALUES(?,?)",id,owner);
        }
        var response=request(admin,"GET","/api/owner-agenda/overview",null,null,false);
        assertEquals(200,response.statusCode(),response.body());
        assertTrue(response.body().contains("Home fixture 0"));assertTrue(response.body().contains("Home fixture 1"));
        assertFalse(response.body().contains("Home fixture 2"));assertFalse(response.body().contains("Home fixture 3"));
        assertTrue(json.readTree(response.body()).path("overdueCount").asInt()>=1);
        assertEquals(today.toString(),json.readTree(response.body()).path("tasks").get(0).path("date").asText());
        jdbc.update("UPDATE owner_task SET done=TRUE WHERE title IN ('Home fixture 0','Home fixture 1')");
        var completed=request(admin,"GET","/api/owner-agenda/overview",null,null,false);
        assertTrue(completed.body().contains("Home fixture 0"));assertTrue(completed.body().contains("Home fixture 1"));
        assertEquals(0,json.readTree(completed.body()).path("dueCount").asInt());
        jdbc.update("UPDATE owner_task SET updated_at=? WHERE title='Home fixture 0'",java.sql.Timestamp.from(today.minusDays(1).atStartOfDay(java.time.ZoneId.of("Europe/Brussels")).toInstant()));
        assertFalse(request(admin,"GET","/api/owner-agenda/overview",null,null,false).body().contains("Home fixture 0"));
        var instructor=staff("INSTRUCTOR",UUID.randomUUID());
        assertEquals(403,request(instructor,"GET","/api/owner-agenda/overview",null,null,false).statusCode());
    }
    HttpClient staff(String role,UUID id) throws Exception {
        String email=id+"@example.test";
        jdbc.update("INSERT INTO cap_user(id,email,display_name,password_hash,role,created_at,staff_role) SELECT ?,?,'Agenda staff',password_hash,'COACH',CURRENT_TIMESTAMP,? FROM cap_user WHERE email='coach@example.test'",id,email,role);
        var c=client();login(c,email,"Coach-test-password-123");return c;
    }
    @Test void sharedTasksValidateOwnersAndProtectConcurrentEdits() throws Exception {
        var admin=client();login(admin,"coach@example.test","Coach-test-password-123");
        UUID owner=jdbc.queryForObject("SELECT id FROM cap_user WHERE email='coach@example.test'",UUID.class),secondId=UUID.randomUUID();
        var second=staff("OWNER",secondId);var instructor=staff("INSTRUCTOR",UUID.randomUUID());var manager=staff("MANAGER",UUID.randomUUID());var finance=staff("FINANCE",UUID.randomUUID());
        var athlete=client();acceptTestAthlete(admin,athlete,"agenda-"+UUID.randomUUID()+"@example.test");
        String path="/api/owner-agenda",id=UUID.randomUUID().toString(),target=path+"/tasks/"+id;
        String body=json.writeValueAsString(Map.of("title","Overleg", "notes","Bespreek planning", "date","2090-02-10","done",false,"version",-1,"assignees",List.of(owner,secondId)));
        assertEquals(401,request(client(),"GET",path+"?month=2090-02",null,null,false).statusCode());
        for(var c:List.of(instructor,manager,finance,athlete)){
            assertEquals(403,request(c,"GET",path+"?month=2090-02",null,null,false).statusCode());
            assertEquals(403,request(c,"PUT",target,body,csrf(c),false).statusCode());
        }
        assertEquals(403,request(admin,"PUT",target,body,null,false).statusCode());
        var created=request(admin,"PUT",target,body,csrf(admin),false);assertEquals(200,created.statusCode(),created.body());
        assertEquals(2,json.readTree(created.body()).path("assignees").size());
        assertTrue(request(second,"GET",path+"?month=2090-02",null,null,false).body().contains("Overleg"));
        assertFalse(request(admin,"GET",path+"?month=2090-03",null,null,false).body().contains("Overleg"));
        String update=created.body().replace("\"done\":false","\"done\":true");
        assertEquals(200,request(second,"PUT",target,update,csrf(second),false).statusCode());
        assertEquals(409,request(admin,"PUT",target,created.body(),csrf(admin),false).statusCode());
        assertEquals(400,request(admin,"GET",path+"?month=invalid",null,null,false).statusCode());
        var invalid=json.readTree(created.body());
        String bad=body.replace(owner.toString(),UUID.randomUUID().toString());
        assertEquals(400,request(admin,"PUT",path+"/tasks/"+UUID.randomUUID(),bad,csrf(admin),false).statusCode());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM owner_task WHERE version<0",Integer.class));
        assertEquals(403,request(instructor,"DELETE",target+"?version=1",null,csrf(instructor),false).statusCode());
        assertEquals(403,request(admin,"DELETE",target+"?version=1",null,null,false).statusCode());
        assertEquals(409,request(admin,"DELETE",target+"?version=0",null,csrf(admin),false).statusCode());
        assertEquals(204,request(second,"DELETE",target+"?version=1",null,csrf(second),false).statusCode());
        assertEquals(204,request(second,"DELETE",target+"?version=1",null,csrf(second),false).statusCode());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM owner_task_assignee WHERE task_id=?",Integer.class,UUID.fromString(id)));
        assertFalse(request(admin,"GET",path+"?month=2090-02",null,null,false).body().contains("Overleg"));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM staff_audit WHERE action='OWNER_TASK_DELETE' AND target_id=?",Integer.class,id));
    }
    @Test void notificationReadsArePrivateToEachOwnerAndIdempotent() throws Exception {
        var admin=client();login(admin,"coach@example.test","Coach-test-password-123");var second=staff("OWNER",UUID.randomUUID());
        var athlete=client();UUID clientId=UUID.fromString(acceptTestAthlete(admin,athlete,"notify-"+UUID.randomUUID()+"@example.test"));UUID event=UUID.randomUUID();
        jdbc.update("INSERT INTO owner_workout_event(id,athlete_id,session_id,workout_name,completed_at) VALUES(?,?,?,?,CURRENT_TIMESTAMP)",event,clientId,"session-"+event,"Workout fixture");
        String base="/api/owner-agenda/notifications";
        assertEquals(1,json.readTree(request(admin,"GET",base,null,null,false).body()).path("unread").asInt());
        for(int i=0;i<2;i++)assertEquals(204,request(admin,"PUT",base+"/"+event+"/read",null,csrf(admin),false).statusCode());
        assertEquals(0,json.readTree(request(admin,"GET",base,null,null,false).body()).path("unread").asInt());
        assertEquals(1,json.readTree(request(second,"GET",base,null,null,false).body()).path("unread").asInt());
        assertEquals(403,request(athlete,"GET",base,null,null,false).statusCode());
    }
}
