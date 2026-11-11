package be.cap.backend;

import be.cap.backend.accounts.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import static org.junit.jupiter.api.Assertions.*;

class MealPlanScheduleTests extends AccountApiTests {
    @org.springframework.test.context.DynamicPropertySource static void database(org.springframework.test.context.DynamicPropertyRegistry r){PostgresTestDatabase.configure(r,"meal_schedule");}
    @Autowired AccountService accounts;@Autowired MealPlanController plans;@Autowired MealPlanSchedule schedules;
    AccountService.Account coach(){return accounts.account("coach@example.test");}
    AccountService.Account athlete(){return accounts.accept(accounts.invite(UUID.randomUUID()+"@example.test",coach().email()).token(),"Schema Client","Athlete-test-password-123");}
    LocalDate today(){return LocalDate.now(ZoneId.of("Europe/Brussels"));}
    MealPlanController.Content content(String name){return new MealPlanController.Content(name,java.util.stream.IntStream.range(0,7).mapToObj(i->new MealPlanController.Day(i,List.of(new MealPlanController.Meal("Dag "+i,List.of(new MealPlanController.Food("Rijst","droog","g",100,348.6,8,77.35,.8)))))).toList());}
    MealPlanController.Plan save(AccountService.Account a,String name){return plans.save(coach()::email,new MealPlanController.Input(null,a.id(),today().with(DayOfWeek.MONDAY),0,content(name)));}
    @Test void flexibleDatesSnapshotsHistoryAndActualDayLoggingStaySeparate()throws Exception{
        var one=athlete();var other=athlete();var plan=save(one,"Eerste schema");assertEquals(1,schedules.history(coach()::email,one.id(),0).size());
        var web=client();login(web,one.email(),"Athlete-test-password-123");String url="/api/meal-plans/self/day?date="+today();assertTrue(json.readTree(request(web,"GET",url,null,null,false).body()).path("name").asText().isEmpty());
        UUID id=UUID.randomUUID();var input=new MealPlanSchedule.Assign(id,plan.version(),today().minusDays(3),today().plusDays(10),false);var assigned=schedules.assign(coach()::email,plan.id(),input);assertEquals(assigned,schedules.assign(coach()::email,plan.id(),input));
        var view=json.readTree(request(web,"GET",url,null,null,false).body());assertEquals("Eerste schema",view.path("name").asText());assertEquals("Dag "+(today().getDayOfWeek().getValue()-1),view.path("meals").get(0).path("name").asText());
        var updated=plans.save(coach()::email,new MealPlanController.Input(plan.id(),one.id(),plan.weekStart(),plan.version(),content("Aangepast privaat schema")));assertEquals(2,schedules.history(coach()::email,one.id(),0).size());assertEquals("Eerste schema",json.readTree(request(web,"GET",url,null,null,false).body()).path("name").asText());
        var consume=new MealPlanSchedule.Consume(today(),0,"lunch",id,plan.id());var entry=schedules.consume(one::email,consume);assertEquals(348.6,entry.calories(),.001);assertEquals(today(),entry.date());assertEquals(entry,schedules.consume(one::email,consume));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM food_diary_entry WHERE id=?",Integer.class,entry.id()));
        var otherWeb=client();login(otherWeb,other.email(),"Athlete-test-password-123");assertTrue(json.readTree(request(otherWeb,"GET",url,null,null,false).body()).path("name").asText().isEmpty());assertThrows(org.springframework.web.server.ResponseStatusException.class,()->schedules.consume(other::email,consume));
        assertEquals(403,request(web,"GET","/api/meal-plans/history?athleteId="+one.id(),null,null,false).statusCode());assertEquals(403,request(web,"POST","/api/meal-plans/"+plan.id()+"/assignments",json.writeValueAsString(input),csrf(web),false).statusCode());
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->schedules.assign(coach()::email,plan.id(),new MealPlanSchedule.Assign(UUID.randomUUID(),updated.version(),today(),today().plusDays(2),false)));
        schedules.assign(coach()::email,plan.id(),new MealPlanSchedule.Assign(UUID.randomUUID(),updated.version(),today(),today().plusDays(2),true));assertEquals("Aangepast privaat schema",json.readTree(request(web,"GET",url,null,null,false).body()).path("name").asText());assertEquals("Eerste schema",json.readTree(request(web,"GET","/api/meal-plans/self/day?date="+today().plusDays(3),null,null,false).body()).path("name").asText());
    }
    @Test void invalidDatesStaleVersionsAndMissingCsrfCannotAssign()throws Exception{
        var a=athlete();var plan=save(a,"Geldig schema");var staff=client();login(staff,coach().email(),"Coach-test-password-123");var input=new MealPlanSchedule.Assign(UUID.randomUUID(),plan.version(),today(),today().plusDays(7),false);assertEquals(403,request(staff,"POST","/api/meal-plans/"+plan.id()+"/assignments",json.writeValueAsString(input),null,false).statusCode());
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->schedules.assign(coach()::email,plan.id(),new MealPlanSchedule.Assign(UUID.randomUUID(),99,today(),today().plusDays(7),false)));assertThrows(org.springframework.web.server.ResponseStatusException.class,()->schedules.assign(coach()::email,plan.id(),new MealPlanSchedule.Assign(UUID.randomUUID(),plan.version(),today(),today().minusDays(1),false)));assertEquals(0,schedules.assignments(coach()::email,a.id()).size());
    }
}
