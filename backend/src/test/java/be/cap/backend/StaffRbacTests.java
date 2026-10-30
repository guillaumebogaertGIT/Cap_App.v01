package be.cap.backend;

import java.net.http.HttpClient;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import static org.junit.jupiter.api.Assertions.*;

@TestPropertySource(properties="spring.datasource.url=jdbc:h2:mem:staff-rbac;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
class StaffRbacTests extends AccountApiTests {
    record Staff(UUID id,String email,HttpClient client){}
    Staff staff(String role) throws Exception {
        UUID id=UUID.randomUUID();String email="rbac-"+id+"@example.test";
        jdbc.update("INSERT INTO cap_user(id,email,display_name,password_hash,role,created_at,staff_role) SELECT ?,?,'Same editable name',password_hash,'COACH',CURRENT_TIMESTAMP,? FROM cap_user WHERE email='coach@example.test'",id,email,role);
        var client=client();login(client,email,"Coach-test-password-123");return new Staff(id,email,client);
    }
    String athlete(HttpClient owner,HttpClient client) throws Exception {
        String email="client-"+UUID.randomUUID()+"@example.test";
        String invitation=json.readTree(request(owner,"POST","/api/accounts/invitations","{\"email\":\""+email+"\"}",csrf(owner),false).body()).path("token").asText();
        var accepted=request(client,"POST","/api/accounts/accept",json.writeValueAsString(Map.of("token",invitation,"name","Private client","password","Client-password-test-123")),csrf(client),false);
        assertEquals(201,accepted.statusCode(),accepted.body());login(client,email,"Client-password-test-123");return json.readTree(accepted.body()).path("id").asText();
    }
    String template(HttpClient owner) throws Exception {
        String body="{\"name\":\"RBAC template\",\"description\":\"Fixed prescription\",\"exercises\":[{\"name\":\"Squat\",\"sets\":2,\"reps\":5}],\"athleteId\":\"\",\"assignedDate\":\"\"}";
        var result=request(owner,"POST","/api/workouts",body,csrf(owner),false);assertEquals(201,result.statusCode(),result.body());return json.readTree(result.body()).path("id").asText();
    }
    String date(){return java.time.LocalDate.now(java.time.ZoneId.of("Europe/Brussels")).plusDays(3).toString();}
    @Test void instructorScopeAssignmentsNotesAndDeniedActionsAreServerEnforced() throws Exception {
        var owner=client();login(owner,"coach@example.test","Coach-test-password-123");var instructor=staff("INSTRUCTOR");
        var client=client();String first=athlete(owner,client),other=athlete(owner,client());String workout=template(owner);
        String before=request(owner,"GET","/api/workouts",null,null,false).body();
        String assign=json.writeValueAsString(Map.of("athleteId",first,"date",date(),"exercises",List.of(Map.of("name","Forged"))));
        String assignPath="/api/workouts/"+workout+"/assignment";
        assertEquals(403,request(instructor.client(),"POST",assignPath,assign,csrf(instructor.client()),false).statusCode());
        String grant="/api/staff/"+instructor.id()+"/clients/"+first;
        assertEquals(204,request(owner,"PUT",grant,null,csrf(owner),false).statusCode());
        assertEquals(200,request(instructor.client(),"POST",assignPath,assign,csrf(instructor.client()),false).statusCode());
        assertEquals(before,request(owner,"GET","/api/workouts",null,null,false).body());
        var assignments=json.readTree(request(instructor.client(),"GET","/api/workout-assignments",null,null,false).body());
        String assignment=assignments.get(0).path("assignmentId").asText();
        assertEquals(instructor.id().toString(),assignments.get(0).path("assignedBy").asText());
        assertTrue(assignments.get(0).path("planned").path("exercises").get(0).path("name").asText().equals("Squat"));
        String attribution=assignments.get(0).path("assignedAt").asText();assertFalse(attribution.isBlank());
        var state=json.readValue(jdbc.queryForObject("SELECT payload FROM workout_state WHERE id=1",String.class),WorkoutStore.State.class);
        var historical=new WorkoutStore.State(state.workouts(),state.assignments().stream().map(a->a.assignmentId().equals(assignment)?new WorkoutStore.DatedWorkout(a.assignmentId(),a.planned(),"LEGACY PRIVATE NOTE",a.weekStart()):a).toList());
        jdbc.update("UPDATE workout_state SET payload=? WHERE id=1",json.writeValueAsString(historical));
        assertFalse(request(client,"GET","/api/workout-assignments",null,null,false).body().contains("LEGACY PRIVATE NOTE"));
        assertEquals(403,request(owner,"PUT","/api/workout-assignments/"+assignment+"/note","{\"note\":\"unattributed\"}",csrf(owner),false).statusCode());
        String note=json.writeValueAsString(Map.of("clientId",first,"assignmentId",assignment,"content","PRIVATE STAFF NOTE","requestId",UUID.randomUUID()));
        var saved=request(instructor.client(),"POST","/api/staff-notes",note,csrf(instructor.client()),false);assertEquals(200,saved.statusCode(),saved.body());
        assertEquals(instructor.id().toString(),json.readTree(saved.body()).path("authorId").asText());
        assertEquals(saved.body(),request(instructor.client(),"POST","/api/staff-notes",note,csrf(instructor.client()),false).body());
        assertEquals(200,request(instructor.client(),"POST",assignPath,assign,csrf(instructor.client()),false).statusCode());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM assignment_attribution WHERE assignment_id=?",Integer.class,assignment));
        assertTrue(jdbc.queryForObject("SELECT payload FROM workout_state WHERE id=1",String.class).contains("LEGACY PRIVATE NOTE"));
        assertEquals(attribution,json.readTree(request(instructor.client(),"GET","/api/workout-assignments",null,null,false).body()).get(0).path("assignedAt").asText());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM staff_note WHERE content='PRIVATE STAFF NOTE'",Integer.class));
        assertFalse(request(client,"GET","/api/workout-assignments",null,null,false).body().contains("PRIVATE STAFF NOTE"));
        assertEquals(403,request(client,"GET","/api/staff-notes?clientId="+first,null,null,false).statusCode());
        assertEquals(403,request(instructor.client(),"GET","/api/staff-notes?clientId="+other,null,null,false).statusCode());
        assertEquals(403,request(instructor.client(),"GET","/api/training?athleteId="+other,null,null,false).statusCode());
        for(String path:List.of("/api/measurements/athletes/"+first,"/api/nutrition-targets?athleteId="+first,"/api/food-diary?date="+date()+"&athleteId="+first,"/api/finance?month=2026-10","/api/staff/members"))
            assertEquals(403,request(instructor.client(),"GET",path,null,null,false).statusCode(),path);
        for(String path:List.of("/api/workouts","/api/exercises","/api/club/sessions","/api/coaches","/api/accounts/invitations"))
            assertEquals(403,request(instructor.client(),"POST",path,"{}",csrf(instructor.client()),false).statusCode(),path);
        assertEquals(403,request(instructor.client(),"PUT","/api/workouts/"+workout,"{}",csrf(instructor.client()),false).statusCode());
        assertEquals(403,request(instructor.client(),"DELETE","/api/workouts/"+workout,null,csrf(instructor.client()),false).statusCode());
        assertEquals(403,request(instructor.client(),"DELETE","/api/staff-notes/"+json.readTree(saved.body()).path("id").asText(),null,csrf(instructor.client()),false).statusCode());
        assertEquals(204,request(owner,"DELETE",grant,null,csrf(owner),false).statusCode());
        assertEquals(403,request(instructor.client(),"POST",assignPath,assign,csrf(instructor.client()),false).statusCode());
        assertEquals("[]",request(instructor.client(),"GET","/api/workout-assignments",null,null,false).body());
    }
    @Test void ownTeachingScheduleAndFinanceBootstrapNeverLeakClubData() throws Exception {
        var owner=client();login(owner,"coach@example.test","Coach-test-password-123");var instructor=staff("INSTRUCTOR");var peer=staff("INSTRUCTOR");var finance=staff("FINANCE");
        for(var teacher:List.of(instructor,peer)){
            String input=json.writeValueAsString(Map.of("title",teacher.id().toString(),"date",date(),"start","18:00","end","19:00","coachId",teacher.id(),"location","CAP","capacity",10,"type","Groep","category","CAP HYROX","repeat",1));
            assertEquals(201,request(owner,"POST","/api/club/sessions",input,csrf(owner),false).statusCode());
        }
        var response=request(instructor.client(),"GET","/api/club/sessions",null,null,false);assertEquals(200,response.statusCode());assertTrue(response.body().contains(instructor.id().toString()));assertFalse(response.body().contains(peer.id().toString()));
        assertFalse(request(instructor.client(),"GET","/app/index.html",null,null,false).body().contains("\"coachId\":\""+peer.id()));
        var html=request(finance.client(),"GET","/app/index.html",null,null,false);assertEquals(200,html.statusCode());assertTrue(html.body().contains("\"people\":[]"));assertTrue(html.body().contains("\"sessions\":[]"));assertTrue(html.body().contains("\"bookings\":[]"));
        for(String path:List.of("/api/workouts","/api/workout-assignments","/api/exercises","/api/club/people","/api/club/sessions","/api/club/bookings","/api/training","/api/records","/api/staff-notes?clientId="+UUID.randomUUID(),"/api/coaches"))
            assertEquals(403,request(finance.client(),"GET",path,null,null,false).statusCode(),path);
        assertEquals(503,request(finance.client(),"GET","/api/finance?month=2026-10",null,null,false).statusCode()); // disabled Stripe, not authorization denial
        for(String action:List.of("checkout","portal","cancel","reconcile"))assertEquals(403,request(finance.client(),"POST","/api/billing/"+action,"{}",csrf(finance.client()),false).statusCode());
    }
    @Test void managerBoundariesRoleRevocationAndDefaultDeny() throws Exception {
        var owner=client();login(owner,"coach@example.test","Coach-test-password-123");var manager=staff("MANAGER");var instructor=staff("INSTRUCTOR");
        assertEquals(200,request(manager.client(),"GET","/api/staff/members",null,null,false).statusCode());template(manager.client());
        String path="/api/staff/"+instructor.id()+"/role";
        assertEquals(403,request(manager.client(),"PUT",path,"{\"role\":\"OWNER\"}",csrf(manager.client()),false).statusCode());
        assertEquals(403,request(manager.client(),"PUT","/api/staff/"+manager.id()+"/role","{\"role\":\"OWNER\"}",csrf(manager.client()),false).statusCode());
        assertEquals(409,request(owner,"PUT","/api/staff/"+jdbc.queryForObject("SELECT id FROM cap_user WHERE email='coach@example.test'",UUID.class)+"/role","{\"role\":\"INSTRUCTOR\"}",csrf(owner),false).statusCode());
        assertEquals(204,request(manager.client(),"PUT",path,"{\"role\":\"FINANCE\"}",csrf(manager.client()),false).statusCode());
        assertEquals(401,request(instructor.client(),"GET","/api/workouts",null,null,false).statusCode());
        login(instructor.client(),instructor.email(),"Coach-test-password-123");assertEquals(403,request(instructor.client(),"GET","/api/workouts",null,null,false).statusCode());
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM staff_audit WHERE action='STAFF_ROLE'",Integer.class)>0);
        for(String role:List.of("UNASSIGNED","DEVELOPMENT")) {
            UUID id=UUID.randomUUID();String email=id+"@example.test";
            jdbc.update("INSERT INTO cap_user(id,email,display_name,password_hash,role,created_at,staff_role) SELECT ?,?,'Glenn',password_hash,'COACH',CURRENT_TIMESTAMP,? FROM cap_user WHERE email='coach@example.test'",id,email,role);
            var denied=client();assertEquals(401,request(denied,"POST","/api/accounts/login","email="+email+"&password=Coach-test-password-123",csrf(denied),true).statusCode());
        }
    }
    @Test void managerCanManageOperationalClassesNutritionAndOrdinaryStaff() throws Exception {
        var manager=staff("MANAGER");var member=client();String clientId=athlete(manager.client(),member);
        var created=request(manager.client(),"POST","/api/coaches",json.writeValueAsString(Map.of("name","Managed instructor","email",UUID.randomUUID()+"@example.test","title","Trainer","password","Staff-temporary-password-123","staffRole","INSTRUCTOR")),csrf(manager.client()),false);
        assertEquals(201,created.statusCode(),created.body());String instructor=json.readTree(created.body()).path("id").asText();
        assertEquals(204,request(manager.client(),"PUT","/api/staff/"+instructor+"/clients/"+clientId,null,csrf(manager.client()),false).statusCode());
        var input=Map.of("title","Managed class","date",date(),"start","16:00","end","17:00","coachId",instructor,"location","CAP","capacity",8,"type","Groep","category","CAP HYROX","repeat",1);
        var session=request(manager.client(),"POST","/api/club/sessions",json.writeValueAsString(input),csrf(manager.client()),false);assertEquals(201,session.statusCode(),session.body());
        String id=json.readTree(session.body()).get(0).path("id").asText();
        assertEquals(200,request(manager.client(),"PUT","/api/club/sessions/"+id,json.writeValueAsString(input),csrf(manager.client()),false).statusCode());
        String targets="{\"targets\":{\"calories\":2200,\"proteinG\":140,\"carbsG\":250,\"fatG\":70,\"waterMl\":2500},\"origin\":\"handmatig\"}";
        assertEquals(200,request(manager.client(),"PUT","/api/nutrition-targets/"+clientId,targets,csrf(manager.client()),false).statusCode());
        assertEquals(200,request(member,"GET","/api/nutrition-targets",null,null,false).statusCode());
        assertEquals(204,request(manager.client(),"DELETE","/api/club/sessions/"+id,null,csrf(manager.client()),false).statusCode());
        assertEquals(204,request(manager.client(),"PUT","/api/coaches/"+instructor+"/active","{\"active\":false}",csrf(manager.client()),false).statusCode());
    }
}
