package be.cap.backend;

import be.cap.backend.accounts.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@TestPropertySource(properties={"cap.ai-coach.enabled=true","cap.ai-coach.testers=coach@example.test"})
class AiNutritionPlannerTests extends AccountApiTests {
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry){PostgresTestDatabase.configure(registry,"ai_nutrition");}
    @Autowired AccountService accounts;@Autowired AiCoachSettings settings;@Autowired NutritionPreferences preferences;
    @MockitoBean AiCoachGateway gateway;
    AccountService.Account coach(){return accounts.account("coach@example.test");}
    AccountService.Account athlete(){return accounts.accept(accounts.invite(UUID.randomUUID()+"@example.test",coach().email()).token(),"Test client","Athlete-test-password-123");}
    @BeforeEach void resetLedger(){jdbc.update("DELETE FROM ai_coach_request");jdbc.update("DELETE FROM ai_coach_settings");jdbc.update("UPDATE ai_coach_budget SET reserved_cents=0 WHERE id=1");when(gateway.configured()).thenReturn(true);}
    void enable(){settings.save(()->coach().email(),new AiCoachSettings.View(0,new AiCoachSettings.Values(true,false,false,false,false,false,true)));}
    Map<String,Object> request(UUID athlete,String scope,Integer day,Integer meal,boolean profile,String allergies,boolean share){
        var content=new MealPlanController.Content("Handmatig schema",java.util.stream.IntStream.range(0,7).mapToObj(i->new MealPlanController.Day(i,i==0?List.of(new MealPlanController.Meal("Ontbijt",List.of(new MealPlanController.Food("Rijst","droog","g",100,348.6,8,77.35,.8))),new MealPlanController.Meal("Handmatige lunch",List.of(new MealPlanController.Food("Eigen product","rauw","g",50,90,4,10,1)),"Zelf ingevulde instructie")):List.of())).toList());
        var result=new LinkedHashMap<String,Object>();result.put("id",UUID.randomUUID());result.put("athleteId",athlete);result.put("instruction","Pas alleen het ontbijt aan");result.put("draft",content);result.put("scope",scope);result.put("day",day);result.put("meal",meal);result.put("shareProfile",profile);result.put("allergies",allergies);result.put("shareAllergies",share);return result;
    }
    String answer(int day,int meal,String food){return "{\"answer\":\"Controleer de porties.\",\"patches\":[{\"day\":"+day+",\"mealIndex\":"+meal+",\"meals\":[{\"name\":\"Nieuw ontbijt\",\"instructions\":\"Kook volgens verpakking\",\"foods\":[{\"name\":\""+food+"\",\"amount\":200,\"calories\":1}]}]}]}";}
    @Test void reviewedMealPatchPreservesManualEditsAndUsesReferenceValues() throws Exception {
        var user=athlete();enable();preferences.save(()->user.email(),new NutritionPreferences.Values("pinda","vis",true,0));jdbc.update("INSERT INTO account_profile(user_id,values_json) VALUES(?,?)",user.id(),json.writeValueAsString(new ProfileController.Values(user.name(),"1990-01-01","",null,null,"Krachtmarker","beginner","PRIVATE_PHONE")));when(gateway.nutritionDraft(anyString(),anyMap())).thenReturn(answer(0,0,"Rijst"));var web=client();login(web,coach().email(),"Coach-test-password-123");
        var input=request(user.id(),"meal",0,0,true,"",false);var response=request(web,"POST","/api/ai-coach/nutrition-draft",json.writeValueAsString(input),csrf(web),false);assertEquals(200,response.statusCode(),response.body());var result=json.readTree(response.body());
        assertTrue(result.path("changed").asBoolean());var days=result.path("content").path("days");assertEquals("Nieuw ontbijt",days.get(0).path("meals").get(0).path("name").asText());assertEquals(348.6,days.get(0).path("meals").get(0).path("foods").get(0).path("calories").asDouble());
        assertEquals("Handmatige lunch",days.get(0).path("meals").get(1).path("name").asText());assertEquals("Zelf ingevulde instructie",days.get(0).path("meals").get(1).path("instructions").asText());assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM meal_plan WHERE athlete_id=?",Integer.class,user.id()));
        var capture=org.mockito.ArgumentCaptor.forClass(Map.class);verify(gateway).nutritionDraft(anyString(),capture.capture());assertTrue(capture.getValue().toString().contains("Krachtmarker"));assertTrue(capture.getValue().toString().contains("pinda"));assertTrue(capture.getValue().toString().contains("vis"));assertFalse(capture.getValue().toString().contains("PRIVATE_PHONE"));assertFalse(capture.getValue().toString().contains("1990-01-01"));
        assertEquals(1,jdbc.queryForObject("SELECT reserved_cents FROM ai_coach_budget WHERE id=1",Integer.class));assertEquals(409,request(web,"POST","/api/ai-coach/nutrition-draft",json.writeValueAsString(input),csrf(web),false).statusCode());
    }
    @Test void consentScopeAndInvalidFoodsAreEnforcedOnServer() throws Exception {
        var user=athlete();var web=client();login(web,coach().email(),"Coach-test-password-123");var input=request(user.id(),"meal",0,0,false,"Pinda",false);
        assertEquals(403,request(web,"POST","/api/ai-coach/nutrition-draft",json.writeValueAsString(input),csrf(web),false).statusCode());enable();assertEquals(400,request(web,"POST","/api/ai-coach/nutrition-draft",json.writeValueAsString(input),csrf(web),false).statusCode());input=request(user.id(),"meal",0,0,false,"",false);input.put("instruction","Klant is allergisch voor pinda's");assertEquals(400,request(web,"POST","/api/ai-coach/nutrition-draft",json.writeValueAsString(input),csrf(web),false).statusCode());verify(gateway,never()).nutritionDraft(anyString(),anyMap());
        input=request(user.id(),"meal",0,0,false,"Pinda",true);assertEquals(400,request(web,"POST","/api/ai-coach/nutrition-draft",json.writeValueAsString(input),csrf(web),false).statusCode());preferences.save(()->user.email(),new NutritionPreferences.Values("pinda","",true,0));
        when(gateway.nutritionDraft(anyString(),anyMap())).thenReturn(answer(1,0,"Rijst"));input=request(user.id(),"meal",0,0,false,"Pinda",true);assertEquals(400,request(web,"POST","/api/ai-coach/nutrition-draft",json.writeValueAsString(input),csrf(web),false).statusCode());
        when(gateway.nutritionDraft(anyString(),anyMap())).thenReturn(answer(0,0,"Onbekend product"));input=request(user.id(),"meal",0,0,false,"",false);assertEquals(400,request(web,"POST","/api/ai-coach/nutrition-draft",json.writeValueAsString(input),csrf(web),false).statusCode());
        assertEquals(2,jdbc.queryForObject("SELECT reserved_cents FROM ai_coach_budget WHERE id=1",Integer.class));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM meal_plan WHERE athlete_id=?",Integer.class,user.id()));
        var athleteWeb=client();login(athleteWeb,user.email(),"Athlete-test-password-123");assertEquals(403,request(athleteWeb,"GET","/api/ai-coach/nutrition-profile?athleteId="+user.id(),null,null,false).statusCode());assertEquals(403,request(athleteWeb,"POST","/api/ai-coach/nutrition-draft",json.writeValueAsString(input),csrf(athleteWeb),false).statusCode());
    }
}
