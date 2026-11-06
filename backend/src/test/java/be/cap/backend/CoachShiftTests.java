package be.cap.backend;

import java.util.*;
import java.net.http.HttpClient;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import static org.junit.jupiter.api.Assertions.*;

@TestPropertySource(properties="spring.datasource.url=jdbc:h2:mem:coach-shifts;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
class CoachShiftTests extends AccountApiTests {
    record Staff(UUID id,HttpClient client){}
    Staff staff(String role) throws Exception {
        UUID id=UUID.randomUUID();String email=id+"@example.test";
        jdbc.update("INSERT INTO cap_user(id,email,display_name,password_hash,role,created_at,staff_role) SELECT ?,?,'Shift coach',password_hash,'COACH',CURRENT_TIMESTAMP,? FROM cap_user WHERE email='coach@example.test'",id,email,role);
        var c=client();login(c,email,"Coach-test-password-123");return new Staff(id,c);
    }
    String input(UUID coach,String start,String end,int version) throws Exception {return json.writeValueAsString(Map.of("coachId",coach,"date","2090-02-10","start",start,"end",end,"note","Open zaal","version",version));}
    @Test void ownerAssignsWorkingHoursCoachesSeeOnlyTheirOwnAndConflictsAreProtected() throws Exception {
        var owner=client();login(owner,"coach@example.test","Coach-test-password-123");
        var one=staff("INSTRUCTOR");var two=staff("INSTRUCTOR");var manager=staff("MANAGER");var finance=staff("FINANCE");var secondOwner=staff("OWNER");
        var athlete=client();acceptTestAthlete(owner,athlete,"shift-"+UUID.randomUUID()+"@example.test");
        String root="/api/coach-shifts",list=root+"?from=2090-02-01&through=2090-02-28",id=UUID.randomUUID().toString(),path=root+"/"+id,body=input(one.id(),"09:00","12:00",-1);
        assertEquals(401,request(client(),"GET",list,null,null,false).statusCode());
        for(var c:List.of(finance.client(),athlete))assertEquals(403,request(c,"GET",list,null,null,false).statusCode());
        for(var c:List.of(one.client(),two.client(),manager.client(),finance.client(),athlete))assertEquals(403,request(c,"PUT",path,body,csrf(c),false).statusCode());
        assertEquals(403,request(owner,"PUT",path,body,null,false).statusCode());
        int sessions=jdbc.queryForObject("SELECT COUNT(*) FROM club_session",Integer.class);
        var created=request(owner,"PUT",path,body,csrf(owner),false);assertEquals(200,created.statusCode(),created.body());
        assertTrue(request(one.client(),"GET",list,null,null,false).body().contains(id));
        assertFalse(request(two.client(),"GET",list,null,null,false).body().contains(id));
        assertFalse(request(manager.client(),"GET",list,null,null,false).body().contains(id));
        assertTrue(request(secondOwner.client(),"GET",list,null,null,false).body().contains(id));
        assertEquals(409,request(owner,"PUT",root+"/"+UUID.randomUUID(),input(one.id(),"11:00","13:00",-1),csrf(owner),false).statusCode());
        assertEquals(200,request(owner,"PUT",root+"/"+UUID.randomUUID(),input(one.id(),"12:00","13:00",-1),csrf(owner),false).statusCode());
        assertEquals(200,request(owner,"PUT",path,input(two.id(),"09:00","12:00",0),csrf(owner),false).statusCode());
        assertFalse(request(one.client(),"GET",list,null,null,false).body().contains(id));assertTrue(request(two.client(),"GET",list,null,null,false).body().contains(id));
        assertEquals(409,request(owner,"PUT",path,input(one.id(),"09:00","10:00",0),csrf(owner),false).statusCode());
        assertEquals(400,request(owner,"PUT",root+"/"+UUID.randomUUID(),input(one.id(),"12:00","09:00",-1),csrf(owner),false).statusCode());
        assertEquals(400,request(owner,"PUT",root+"/"+UUID.randomUUID(),input(UUID.randomUUID(),"09:00","10:00",-1),csrf(owner),false).statusCode());
        assertEquals(400,request(owner,"GET",root+"?from=2090-02-10&through=2092-02-10",null,null,false).statusCode());
        assertEquals(403,request(two.client(),"DELETE",path+"?version=1",null,csrf(two.client()),false).statusCode());
        assertEquals(409,request(owner,"DELETE",path+"?version=0",null,csrf(owner),false).statusCode());
        assertEquals(204,request(secondOwner.client(),"DELETE",path+"?version=1",null,csrf(secondOwner.client()),false).statusCode());
        assertFalse(request(two.client(),"GET",list,null,null,false).body().contains(id));
        assertEquals(sessions,jdbc.queryForObject("SELECT COUNT(*) FROM club_session",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM staff_audit WHERE action='COACH_SHIFT_DELETE' AND target_id=?",Integer.class,id));
    }
    @Test void historicalHoursAreRetainedAndCannotBeRewritten() throws Exception {
        var owner=client();login(owner,"coach@example.test","Coach-test-password-123");var instructor=staff("INSTRUCTOR");UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO coach_shift(id,coach_id,shift_date,start_time,end_time,note) VALUES(?,?,'2020-01-01','09:00','10:00','Historical')",id,instructor.id());
        String path="/api/coach-shifts/"+id;
        assertEquals(400,request(owner,"PUT",path,input(instructor.id(),"09:00","10:00",0),csrf(owner),false).statusCode());
        assertEquals(400,request(owner,"DELETE",path+"?version=0",null,csrf(owner),false).statusCode());
        assertTrue(request(instructor.client(),"GET","/api/coach-shifts?from=2020-01-01&through=2020-01-01",null,null,false).body().contains("Historical"));
    }
}
