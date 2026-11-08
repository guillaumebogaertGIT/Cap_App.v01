package be.cap.backend;

import be.cap.backend.accounts.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import static org.junit.jupiter.api.Assertions.*;

class MealPlanTests extends AccountApiTests {
    @Autowired AccountService accounts;
    @Autowired AiOwnContext context;
    AccountService.Account coach(){return accounts.account("coach@example.test");}
    AccountService.Account athlete(){return accounts.accept(accounts.invite(UUID.randomUUID()+"@example.test",coach().email()).token(),"Meal client","Athlete-test-password-123");}
    LocalDate week(){return LocalDate.now(ZoneId.of("Europe/Brussels")).with(DayOfWeek.MONDAY);}
    MealPlanController.Content content(String name,double amount){return new MealPlanController.Content(name,java.util.stream.IntStream.range(0,7).mapToObj(i->new MealPlanController.Day(i,i<2?List.of(new MealPlanController.Meal("Lunch",List.of(new MealPlanController.Food("Rijst","droog","g",amount,348.6,8,77.35,.8)))):List.of())).toList());}
    @Test void draftsPublicationVersionsAndOwnShoppingContextStaySeparate() throws Exception {
        var first=athlete();var second=athlete();var staff=client();login(staff,coach().email(),"Coach-test-password-123");var owner=client();login(owner,first.email(),"Athlete-test-password-123");var other=client();login(other,second.email(),"Athlete-test-password-123");
        int diaryBefore=jdbc.queryForObject("SELECT COUNT(*) FROM food_diary_entry",Integer.class);
        var input=new MealPlanController.Input(null,first.id(),week(),0,content("Client week",100));
        var saved=request(staff,"POST","/api/meal-plans",json.writeValueAsString(input),csrf(staff),false);assertEquals(200,saved.statusCode(),saved.body());var plan=json.readValue(saved.body(),MealPlanController.Plan.class);
        assertEquals(0,json.readTree(request(owner,"GET","/api/meal-plans/self?weekStart="+week(),null,null,false).body()).size());
        assertFalse(context.read(first,false,false,false,false).contains("publishedMealPlan"));
        assertEquals(403,request(owner,"GET","/api/meal-plans?athleteId="+second.id()+"&weekStart="+week(),null,null,false).statusCode());
        assertEquals(403,request(owner,"POST","/api/meal-plans",json.writeValueAsString(input),csrf(owner),false).statusCode());
        var published=request(staff,"POST","/api/meal-plans/"+plan.id()+"/publish","{\"version\":1}",csrf(staff),false);assertEquals(200,published.statusCode(),published.body());
        String snapshot=context.read(first,false,false,false,false,true);assertTrue(snapshot.contains("Rijst"));assertTrue(snapshot.contains("200.0"));assertFalse(context.read(second,false,false,false,false,true).contains("Rijst"));
        var edit=new MealPlanController.Input(plan.id(),first.id(),week(),2,content("PRIVATE_DRAFT",300));assertEquals(200,request(staff,"POST","/api/meal-plans",json.writeValueAsString(edit),csrf(staff),false).statusCode());
        String view=request(owner,"GET","/api/meal-plans/self?weekStart="+week()+"&athleteId="+second.id(),null,null,false).body();assertTrue(view.contains("Client week"));assertFalse(view.contains("PRIVATE_DRAFT"));assertFalse(context.read(first,false,false,false,false,true).contains("PRIVATE_DRAFT"));
        assertEquals(0,json.readTree(request(other,"GET","/api/meal-plans/self?weekStart="+week()+"&athleteId="+first.id(),null,null,false).body()).size());
        assertEquals(409,request(staff,"POST","/api/meal-plans",json.writeValueAsString(edit),csrf(staff),false).statusCode());
        assertEquals(409,request(staff,"POST","/api/meal-plans/"+plan.id()+"/publish","{\"version\":2}",csrf(staff),false).statusCode());
        assertEquals(200,request(staff,"DELETE","/api/meal-plans/"+plan.id()+"/publication?version=3",null,csrf(staff),false).statusCode());
        assertEquals(0,json.readTree(request(owner,"GET","/api/meal-plans/self?weekStart="+week(),null,null,false).body()).size());assertEquals(diaryBefore,jdbc.queryForObject("SELECT COUNT(*) FROM food_diary_entry",Integer.class));
    }
    @Test void templatesAndInvalidPortionsCannotBecomeClientPlans() throws Exception {
        var staff=client();login(staff,coach().email(),"Coach-test-password-123");
        var template=new MealPlanController.Input(null,null,null,0,content("Reusable template",80));var saved=request(staff,"POST","/api/meal-plans",json.writeValueAsString(template),csrf(staff),false);assertEquals(200,saved.statusCode(),saved.body());var id=json.readTree(saved.body()).path("id").asText();
        assertEquals(400,request(staff,"POST","/api/meal-plans/"+id+"/publish","{\"version\":1}",csrf(staff),false).statusCode());
        assertEquals(400,request(staff,"POST","/api/meal-plans",json.writeValueAsString(new MealPlanController.Input(null,null,null,0,content("Invalid",-1))),csrf(staff),false).statusCode());
        assertEquals(200,request(staff,"GET","/api/meal-plans/foods",null,null,false).statusCode());
        assertTrue(request(staff,"GET","/api/meal-plans/templates",null,null,false).body().contains("Reusable template"));
    }
}
