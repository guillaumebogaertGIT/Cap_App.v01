package be.cap.backend;

import java.util.*;
import java.time.*;
import java.net.http.HttpClient;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import static org.junit.jupiter.api.Assertions.*;
import be.cap.backend.accounts.billing.ComplimentaryMemberships;

@TestPropertySource(properties="spring.datasource.url=jdbc:h2:mem:remaining-features;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
class RemainingFeaturesTests extends AccountApiTests {
    @Test void chatContactPhotosFollowConversationAccess() throws Exception {
        var owner=client();login(owner,"coach@example.test","Coach-test-password-123");var first=athlete(owner);var second=athlete(owner);
        UUID coach=jdbc.queryForObject("SELECT id FROM cap_user WHERE email='coach@example.test'",UUID.class);
        jdbc.update("INSERT INTO account_profile(user_id,values_json,photo) VALUES(?,'{}',?) ON CONFLICT(user_id) DO UPDATE SET photo=EXCLUDED.photo",coach,new byte[]{1,2,3});
        String path="/api/social/contacts/"+coach+"/photo";
        assertEquals(200,request(first.client(),"GET",path,null,null,false).statusCode());
        assertTrue(request(first.client(),"GET","/api/social/inbox",null,null,false).body().contains(path));
        assertEquals(401,request(client(),"GET",path,null,null,false).statusCode());
        jdbc.update("INSERT INTO account_profile(user_id,values_json,photo) VALUES(?,'{}',?)",second.id(),new byte[]{4,5,6});
        assertEquals(404,request(first.client(),"GET","/api/social/contacts/"+second.id()+"/photo",null,null,false).statusCode());
        jdbc.update("UPDATE cap_user SET active=FALSE WHERE id=?",coach);
        try{assertEquals(404,request(first.client(),"GET",path,null,null,false).statusCode());}
        finally{jdbc.update("UPDATE cap_user SET active=TRUE WHERE id=?",coach);}
    }
    @Test void communityChannelsSeparatePostsAndNotifyEveryoneWithoutSubscriptions() throws Exception {
        var owner=client();login(owner,"coach@example.test","Coach-test-password-123");var first=athlete(owner);var second=athlete(owner);var teacher=instructor();
        UUID general=UUID.randomUUID(),hyrox=UUID.randomUUID(),move=UUID.randomUUID();
        String generalBody=json.writeValueAsString(Map.of("id",general,"content","General club announcement"));
        String hyroxBody=json.writeValueAsString(Map.of("id",hyrox,"content","HYROX club announcement","channel","hyrox"));
        String moveBody=json.writeValueAsString(Map.of("id",move,"content","MOVE club announcement","channel","move"));
        assertEquals(200,request(owner,"POST","/api/social/community",generalBody,csrf(owner),false).statusCode());
        assertEquals(200,request(owner,"POST","/api/social/community",hyroxBody,csrf(owner),false).statusCode());
        assertEquals(200,request(owner,"POST","/api/social/community",hyroxBody,csrf(owner),false).statusCode());
        assertEquals(409,request(owner,"POST","/api/social/community",hyroxBody.replace("hyrox","hybrid"),csrf(owner),false).statusCode());
        assertEquals(200,request(first.client(),"POST","/api/social/community",moveBody,csrf(first.client()),false).statusCode());
        assertEquals(403,request(second.client(),"POST","/api/social/community",moveBody,null,false).statusCode());
        String generalFeed=request(second.client(),"GET","/api/social/community",null,null,false).body();assertTrue(generalFeed.contains("General club announcement"));assertFalse(generalFeed.contains("HYROX club announcement"));
        String hyroxFeed=request(second.client(),"GET","/api/social/community?channel=hyrox",null,null,false).body();assertTrue(hyroxFeed.contains("HYROX club announcement"));assertFalse(hyroxFeed.contains("General club announcement"));
        for(var person:List.of(first,second,teacher)){
            var alerts=json.readTree(request(person.client(),"GET","/api/social/notifications",null,null,false).body());
            assertTrue(alerts.path("items").toString().contains("HYROX club announcement"));assertTrue(alerts.path("items").toString().contains("\"channel\":\"hyrox\""));
            var channels=json.readTree(request(person.client(),"GET","/api/social/community/channels",null,null,false).body());assertEquals(6,channels.size());assertTrue(channels.toString().contains("Open gym"));
        }
        assertFalse(request(owner,"GET","/api/social/notifications",null,null,false).body().contains("HYROX club announcement"));
        assertTrue(request(owner,"GET","/api/social/notifications",null,null,false).body().contains("MOVE club announcement"));
        assertEquals(204,request(second.client(),"PUT","/api/social/notifications/read",json.writeValueAsString(Map.of("ids",List.of(hyrox))),csrf(second.client()),false).statusCode());
        var counts=json.readTree(request(second.client(),"GET","/api/social/community/channels",null,null,false).body());
        for(var channel:counts){if(channel.path("id").asText().equals("hyrox"))assertEquals(0,channel.path("unread").asInt());if(channel.path("id").asText().equals("move"))assertEquals(1,channel.path("unread").asInt());}
        assertTrue(request(first.client(),"GET","/api/social/notifications",null,null,false).body().contains("HYROX club announcement"));
        assertEquals(400,request(second.client(),"GET","/api/social/community?channel=unknown",null,null,false).statusCode());
        assertEquals(400,request(owner,"POST","/api/social/community",json.writeValueAsString(Map.of("id",UUID.randomUUID(),"content","No channel","channel","unknown")),csrf(owner),false).statusCode());
        assertEquals(401,request(client(),"GET","/api/social/community/channels",null,null,false).statusCode());
        assertEquals(403,request(second.client(),"DELETE","/api/social/messages/"+hyrox,null,csrf(second.client()),false).statusCode());
        assertEquals(204,request(owner,"DELETE","/api/social/messages/"+hyrox,null,csrf(owner),false).statusCode());assertFalse(request(first.client(),"GET","/api/social/notifications",null,null,false).body().contains("HYROX club announcement"));
    }
    @Test void attachmentsArePrivateValidatedAndRemovedWithMessage() throws Exception {
        var owner=client();login(owner,"coach@example.test","Coach-test-password-123");var first=athlete(owner);var second=athlete(owner);var stranger=athlete(owner);
        for(var person:List.of(first,second))assertEquals(200,request(person.client(),"PUT","/api/social/privacy","{\"discoverable\":true}",csrf(person.client()),false).statusCode());
        UUID id=UUID.randomUUID();String path="/api/social/direct/"+second.id();
        String body=json.writeValueAsString(Map.of("id",id,"content","","attachment",Map.of("name","schema.pdf","data",Base64.getEncoder().encodeToString("%PDF-1.7\nTest attachment".getBytes(java.nio.charset.StandardCharsets.UTF_8)))));
        assertEquals(403,request(first.client(),"POST",path,body,null,false).statusCode());
        assertEquals(200,request(first.client(),"POST",path,body,csrf(first.client()),false).statusCode());
        assertEquals(200,request(first.client(),"POST",path,body,csrf(first.client()),false).statusCode());
        String download="/api/social/attachments/"+id;
        var response=request(second.client(),"GET",download,null,null,false);assertEquals(200,response.statusCode());assertTrue(response.body().startsWith("%PDF-"));assertTrue(response.headers().firstValue("Content-Disposition").orElseThrow().startsWith("attachment;"));assertEquals("nosniff",response.headers().firstValue("X-Content-Type-Options").orElseThrow());
        assertTrue(request(second.client(),"GET","/api/social/direct/"+first.id(),null,null,false).body().contains("schema.pdf"));
        assertEquals(404,request(owner,"GET",download,null,null,false).statusCode());assertEquals(404,request(stranger.client(),"GET",download,null,null,false).statusCode());assertEquals(401,request(client(),"GET",download,null,null,false).statusCode());
        for(String name:List.of("test.exe","../file.pdf","fake.png")){
            String invalid=json.writeValueAsString(Map.of("id",UUID.randomUUID(),"content","","attachment",Map.of("name",name,"data",Base64.getEncoder().encodeToString("not an image".getBytes()))));
            assertEquals(400,request(first.client(),"POST",path,invalid,csrf(first.client()),false).statusCode());
        }
        String oversized=json.writeValueAsString(Map.of("id",UUID.randomUUID(),"content","","attachment",Map.of("name","too-big.txt","data",Base64.getEncoder().encodeToString(new byte[10485761]))));
        assertEquals(400,request(first.client(),"POST",path,oversized,csrf(first.client()),false).statusCode());
        assertEquals(200,request(second.client(),"PUT","/api/social/privacy","{\"discoverable\":false}",csrf(second.client()),false).statusCode());assertEquals(404,request(first.client(),"GET",download,null,null,false).statusCode());
        assertEquals(200,request(second.client(),"PUT","/api/social/privacy","{\"discoverable\":true}",csrf(second.client()),false).statusCode());
        assertEquals(204,request(first.client(),"DELETE","/api/social/messages/"+id,null,csrf(first.client()),false).statusCode());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM social_attachment WHERE message_id=?",Integer.class,id));assertEquals(404,request(second.client(),"GET",download,null,null,false).statusCode());
    }
    record Person(UUID id,HttpClient client){}
    Person instructor() throws Exception{UUID id=UUID.randomUUID();String email=id+"@example.test";jdbc.update("INSERT INTO cap_user(id,email,display_name,password_hash,role,created_at,staff_role) SELECT ?,?,'Test instructor',password_hash,'COACH',CURRENT_TIMESTAMP,'INSTRUCTOR' FROM cap_user WHERE email='coach@example.test'",id,email);var c=client();login(c,email,"Coach-test-password-123");return new Person(id,c);}
    Person athlete(HttpClient owner) throws Exception{var c=client();String id=acceptTestAthlete(owner,c,"feature-"+UUID.randomUUID()+"@example.test");return new Person(UUID.fromString(id),c);}
    @Test void coachesOwnNutritionIsPrivateAndDoesNotGrantClientAccess() throws Exception {
        var owner=client();login(owner,"coach@example.test","Coach-test-password-123");var one=instructor();var two=instructor();var client=athlete(owner);
        String targets="{\"targets\":{\"calories\":2200,\"proteinG\":140,\"carbsG\":250,\"fatG\":65,\"waterMl\":2000},\"origin\":\"handmatig\"}";
        assertEquals(200,request(one.client(),"PUT","/api/nutrition-targets/self",targets,csrf(one.client()),false).statusCode());
        assertTrue(request(one.client(),"GET","/api/nutrition-targets/self",null,null,false).body().contains("2200"));
        assertEquals(204,request(two.client(),"GET","/api/nutrition-targets/self",null,null,false).statusCode());
        assertEquals(403,request(one.client(),"GET","/api/nutrition-targets?athleteId="+client.id(),null,null,false).statusCode());
        assertEquals(403,request(client.client(),"PUT","/api/nutrition-targets/self",targets,csrf(client.client()),false).statusCode());
        String entry=json.writeValueAsString(Map.of("id",UUID.randomUUID(),"date",LocalDate.now(ZoneId.of("Europe/Brussels")),"meal","breakfast","name","Own meal","amount",100,"unit","g","calories",200,"proteinG",10,"carbsG",30,"fatG",4));
        assertEquals(201,request(one.client(),"POST","/api/food-diary",entry,csrf(one.client()),false).statusCode());
        assertTrue(request(one.client(),"GET","/api/food-diary?date="+LocalDate.now(ZoneId.of("Europe/Brussels")),null,null,false).body().contains("Own meal"));
        assertEquals(403,request(two.client(),"GET","/api/food-diary?date="+LocalDate.now(ZoneId.of("Europe/Brussels"))+"&athleteId="+one.id(),null,null,false).statusCode());
    }
    @Test void complimentaryAccessIsDatedAuditedAndOwnerOnly() throws Exception{
        var owner=client();login(owner,"coach@example.test","Coach-test-password-123");var client=athlete(owner);var coach=instructor();UUID id=UUID.randomUUID();var day=LocalDate.now(ZoneId.of("Europe/Brussels"));
        String body=json.writeValueAsString(Map.of("athleteId",client.id(),"offer","full_1m","start",day,"end",day.plusDays(30),"reason","Existing membership migration"));String path="/api/complimentary-memberships/"+id;
        assertEquals(403,request(coach.client(),"PUT",path,body,csrf(coach.client()),false).statusCode());
        assertEquals(403,request(owner,"PUT",path,body,null,false).statusCode());
        var granted=request(owner,"PUT",path,body,csrf(owner),false);assertEquals(200,granted.statusCode(),granted.body());
        assertTrue(ComplimentaryMemberships.access(jdbc,client.id(),Instant.now()));
        assertFalse(ComplimentaryMemberships.access(jdbc,client.id(),day.plusDays(31).atStartOfDay(ZoneId.of("Europe/Brussels")).toInstant()));
        assertTrue(json.readTree(request(client.client(),"GET","/api/billing",null,null,false).body()).path("membershipAccess").asBoolean());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM billing_account WHERE user_id=?",Integer.class,client.id()));
        assertEquals(204,request(owner,"DELETE",path,null,csrf(owner),false).statusCode());assertFalse(ComplimentaryMemberships.access(jdbc,client.id(),Instant.now()));
    }
    @Test void socialContactsRespectOptInLinksAndSessionMembership() throws Exception{
        var owner=client();login(owner,"coach@example.test","Coach-test-password-123");var first=athlete(owner);var second=athlete(owner);var teacher=instructor();
        String direct="/api/social/direct/"+second.id();String text=json.writeValueAsString(Map.of("id",UUID.randomUUID(),"content","Private progress"));
        assertEquals(403,request(first.client(),"POST",direct,text,csrf(first.client()),false).statusCode());
        assertEquals(200,request(first.client(),"PUT","/api/social/privacy","{\"discoverable\":true}",csrf(first.client()),false).statusCode());
        assertEquals(200,request(second.client(),"PUT","/api/social/privacy","{\"discoverable\":true}",csrf(second.client()),false).statusCode());
        assertEquals(200,request(first.client(),"POST",direct,text,csrf(first.client()),false).statusCode());assertEquals(200,request(first.client(),"POST",direct,text,csrf(first.client()),false).statusCode());
        assertTrue(request(second.client(),"GET","/api/social/direct/"+first.id(),null,null,false).body().contains("Private progress"));
        assertFalse(request(owner,"GET","/api/social/direct/"+first.id(),null,null,false).body().contains("Private progress"));
        assertTrue(request(second.client(),"GET","/api/social/notifications",null,null,false).body().contains("Private progress"));
        assertTrue(request(second.client(),"GET","/api/social/inbox",null,null,false).body().contains("Private progress"));
        assertFalse(request(owner,"GET","/api/social/inbox",null,null,false).body().contains("Private progress"));
        assertFalse(request(owner,"GET","/api/social/notifications",null,null,false).body().contains("Private progress"));
        UUID message=UUID.fromString(json.readTree(text).path("id").asText());assertEquals(403,request(owner,"DELETE","/api/social/messages/"+message,null,csrf(owner),false).statusCode());
        String read=json.writeValueAsString(Map.of("ids",List.of(message)));
        assertEquals(404,request(owner,"PUT","/api/social/notifications/read",read,csrf(owner),false).statusCode());
        assertEquals(403,request(second.client(),"PUT","/api/social/notifications/read",read,null,false).statusCode());
        assertEquals(204,request(second.client(),"PUT","/api/social/notifications/read",read,csrf(second.client()),false).statusCode());
        assertEquals(204,request(second.client(),"PUT","/api/social/notifications/read",read,csrf(second.client()),false).statusCode());
        assertFalse(request(second.client(),"GET","/api/social/notifications",null,null,false).body().contains("Private progress"));
        assertEquals(204,request(second.client(),"DELETE","/api/social/notifications/read",read,csrf(second.client()),false).statusCode());
        assertTrue(request(second.client(),"GET","/api/social/notifications",null,null,false).body().contains("Private progress"));
        assertEquals(404,request(owner,"DELETE","/api/social/notifications/read",read,csrf(owner),false).statusCode());
        assertEquals(204,request(second.client(),"PUT","/api/social/notifications/read",read,csrf(second.client()),false).statusCode());
        UUID announcement=UUID.randomUUID();
        assertEquals(200,request(owner,"POST","/api/social/community",json.writeValueAsString(Map.of("id",announcement,"content","Community announcement")),csrf(owner),false).statusCode());
        assertTrue(request(first.client(),"GET","/api/social/notifications",null,null,false).body().contains("Community announcement"));
        assertFalse(request(owner,"GET","/api/social/notifications",null,null,false).body().contains("Community announcement"));
        assertEquals(204,request(first.client(),"PUT","/api/social/notifications/read",json.writeValueAsString(Map.of("ids",List.of(announcement))),csrf(first.client()),false).statusCode());
        assertTrue(request(second.client(),"GET","/api/social/notifications",null,null,false).body().contains("Community announcement"));
        assertEquals(403,request(teacher.client(),"GET","/api/social/direct/"+first.id(),null,null,false).statusCode());
        jdbc.update("INSERT INTO instructor_client(instructor_id,client_id,granted_by,granted_at) SELECT ?,?,id,CURRENT_TIMESTAMP FROM cap_user WHERE email='coach@example.test'",teacher.id(),first.id());
        assertEquals(200,request(teacher.client(),"GET","/api/social/direct/"+first.id(),null,null,false).statusCode());
        UUID session=UUID.randomUUID();jdbc.update("INSERT INTO club_session(id,title,session_date,start_time,end_time,coach_id,location,capacity,session_type,category,program) VALUES(?,'Group room','2090-02-10','10:00','11:00',?,'Test',8,'Groep','Strength','')",session,teacher.id());jdbc.update("INSERT INTO session_booking(session_id,athlete_id) VALUES(?,?)",session,first.id());
        String group="/api/social/groups/"+session;
        assertEquals(200,request(first.client(),"POST",group,json.writeValueAsString(Map.of("id",UUID.randomUUID(),"content","Group progress")),csrf(first.client()),false).statusCode());
        assertEquals(403,request(second.client(),"GET",group,null,null,false).statusCode());assertTrue(request(teacher.client(),"GET",group,null,null,false).body().contains("Group progress"));
        assertTrue(request(teacher.client(),"GET","/api/social/notifications",null,null,false).body().contains("Group progress"));
        assertFalse(request(second.client(),"GET","/api/social/notifications",null,null,false).body().contains("Group progress"));
        jdbc.update("UPDATE session_booking SET cancelled=TRUE WHERE session_id=? AND athlete_id=?",session,first.id());assertEquals(403,request(first.client(),"GET",group,null,null,false).statusCode());
        assertEquals(401,request(client(),"GET","/api/social/community",null,null,false).statusCode());
    }
    @Test void mailRemainsDisabledAndPreferencesArePersonal() throws Exception{
        var owner=client();login(owner,"coach@example.test","Coach-test-password-123");var client=athlete(owner);
        assertFalse(json.readTree(request(client(),"GET","/api/accounts/recovery",null,null,false).body()).path("enabled").asBoolean());
        assertEquals(503,request(client.client(),"POST","/api/accounts/recovery","{\"email\":\"nobody@example.test\"}",csrf(client.client()),false).statusCode());
        assertEquals(200,request(client.client(),"PUT","/api/email-preferences","{\"assignments\":false,\"reminders\":false}",csrf(client.client()),false).statusCode());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM email_outbox",Integer.class));
    }
}
