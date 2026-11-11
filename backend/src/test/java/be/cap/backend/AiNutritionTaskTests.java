package be.cap.backend;

import be.cap.backend.accounts.*;
import java.time.*;
import java.util.*;
import java.net.http.HttpClient;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@TestPropertySource(properties={"cap.ai-coach.enabled=true","cap.ai-coach.testers=coach@example.test"})
class AiNutritionTaskTests extends AccountApiTests {
    @org.springframework.test.context.DynamicPropertySource static void database(org.springframework.test.context.DynamicPropertyRegistry r){PostgresTestDatabase.configure(r,"ai_nutrition_tasks");}
    @Autowired AiNutritionTasks tasks;@Autowired AiCoachActions actions;@Autowired AiCoachSettings settings;@Autowired AccountService accounts;
    @Autowired NutritionController nutrition;@Autowired NutritionPreferences preferences;@Autowired MealPlanController meals;
    @MockitoBean AiCoachGateway gateway;
    AccountService.Account actor(){return accounts.account("coach@example.test");}
    @BeforeEach void setup(){jdbc.update("DELETE FROM ai_nutrition_task");jdbc.update("DELETE FROM ai_coach_action");jdbc.update("DELETE FROM ai_coach_settings");jdbc.update("DELETE FROM ai_coach_request");jdbc.update("UPDATE ai_coach_budget SET reserved_cents=0");when(gateway.configured()).thenReturn(true);settings.save(()->actor().email(),new AiCoachSettings.View(0,new AiCoachSettings.Values(true,false,false,false,false,false,true)));}
    AccountService.Account athlete(String name){var a=accounts.accept(accounts.invite(UUID.randomUUID()+"@example.test",actor().email()).token(),name,"Athlete-test-password-123");preferences.save(a::email,new NutritionPreferences.Values("","",true,0));return a;}
    void targets(AccountService.Account a){nutrition.save(actor()::email,a.id(),new NutritionController.Input(new NutritionController.Targets(3000.0,180.0,345.0,100.0,2000.0),"berekend"));}
    HttpClient coach()throws Exception{var c=client();login(c,actor().email(),"Coach-test-password-123");return c;}
    tools.jackson.databind.JsonNode send(HttpClient c,String message,tools.jackson.databind.JsonNode previous)throws Exception{
        var data=new LinkedHashMap<String,Object>();data.put("id",UUID.randomUUID());data.put("message",message);data.put("conversationId",previous==null?null:previous.path("conversationId").asText());data.put("revision",previous==null?0:previous.path("revision").asInt());var result=request(c,"POST","/api/ai-coach/nutrition-chat",json.writeValueAsString(data),csrf(c),false);assertEquals(200,result.statusCode(),result.body());return json.readTree(result.body());
    }
    Map<String,Object> plan(boolean changed){var days=new ArrayList<Map<String,Object>>();String[] starch={"Rijst","Volkoren pasta","Zoete aardappel","Volkoren brood"};
        for(int i=0;i<7;i++){String fruit=(i+(changed?1:0))%2==0?"Banaan":"Blauwe bessen",main=starch[(i+(changed?1:0))%4];days.add(Map.of("day",i,"meals",List.of(
            meal("Ontbijt",food("Havermout",100),food("Magere kwark",250),food(fruit,150)),
            meal("Lunch",food("Mager vlees",200),food(main,180),food("Groenten",200)),
            meal("Avondeten",food("Magere vis",200),food("Rijst",150),food("Groenten",200),food("Ongezouten notenmix",70)),
            meal("Tussendoor",food("Magere kwark",200),food("Honing",40),food("Frambozen",150))
        )));}return Map.of("name","Gevarieerd weekconcept","days",days);
    }
    Map<String,Object> food(String name,int amount){return Map.of("name",name,"amount",amount);}
    @SafeVarargs final Map<String,Object> meal(String name,Map<String,Object>... foods){return Map.of("name",name,"instructions","Bereid volgens de vermelde toestand en portie.","foods",List.of(foods));}
    AiCoachGateway.ToolReply proposal(Map<String,Object> p){return new AiCoachGateway.ToolReply(List.of(Map.of("type","function_call","name","propose_nutrition_plan","call_id","proposal","arguments",json.writeValueAsString(p))),List.of(new AiCoachGateway.ToolCall("proposal","propose_nutrition_plan",json.writeValueAsString(p))),"");}
    @Test void savedCalculationsDriveCompleteReviewThenIdempotentPrivateSaveAndRevision()throws Exception{
        var a=athlete("Guillaume Nutritionflow");targets(a);var c=coach();when(gateway.nutritionTools(anyList(),anyList(),anyString())).thenAnswer(inv->{String input=json.writeValueAsString(inv.getArgument(0));assertTrue(input.contains("3000"));assertTrue(input.contains("berekend"));assertTrue(input.contains(a.id().toString()));assertFalse(input.contains("password_hash"));return proposal(plan(false));});
        var first=send(c,"Maak een voedingsschema voor Guillaume Nutritionflow voor 2026-11-16",null);assertEquals("review",first.path("nutritionTask").path("status").asText(),first.toString());assertTrue(first.path("answer").asText().contains("3000 kcal"));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM meal_plan WHERE athlete_id=?",Integer.class,a.id()));
        var action=first.path("proposal").path("actions").get(0);var content=json.treeToValue(action.path("data").path("content"),MealPlanController.Content.class);assertEquals(7,content.days().size());for(var day:content.days()){var t=NutritionPlanValidator.totals(day.meals());assertTrue(Math.abs(t.calories()-3000)<=150,t.toString());assertTrue(Math.abs(t.proteinG()-180)<=10,t.toString());}
        UUID id=UUID.fromString(first.path("proposal").path("id").asText());var receipt=actions.confirm(actor(),id);assertEquals(receipt,actions.confirm(actor(),id));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM meal_plan WHERE athlete_id=?",Integer.class,a.id()));assertTrue(meals.self(a::email,LocalDate.of(2026,11,16)).isEmpty());
        var restored=tasks.restore(actor(),UUID.fromString(first.path("conversationId").asText()));assertEquals("saved",((NutritionTaskRules.State)restored.get("nutritionTask")).status);
        doReturn(proposal(plan(true))).when(gateway).nutritionTools(anyList(),anyList(),anyString());
        var revised=send(c,"Meer variatie",first);assertEquals("review",revised.path("nutritionTask").path("status").asText(),revised.toString());assertEquals(a.id().toString(),revised.path("nutritionTask").path("clientId").asText());assertEquals("2026-11-16",revised.path("nutritionTask").path("weekStart").asText());assertEquals(3000,revised.path("nutritionTask").path("targets").path("calories").asInt());
        var second=send(c,"Nog meer variatie",revised);assertThrows(org.springframework.web.server.ResponseStatusException.class,()->actions.confirm(actor(),UUID.fromString(revised.path("proposal").path("id").asText())));assertTrue(second.path("answer").asText().length()>0);
        assertEquals(3000.0,jdbc.queryForObject("SELECT calories FROM nutrition_target WHERE athlete_id=?",Double.class,a.id()));
    }
    @Test void ambiguityMissingTargetsAndExplicitOverridesRetainFocusedState()throws Exception{
        athlete("Ambiguityname Alpha");var b=athlete("Ambiguityname Beta");var c=coach();var ambiguous=send(c,"Maak een voedingsschema voor Ambiguityname 16-22 november 2026, 3000 kcal en 180 g eiwit",null);assertEquals(2,ambiguous.path("nutritionTask").path("candidates").size());verify(gateway,never()).nutritionTools(anyList(),anyList(),anyString());
        when(gateway.nutritionTools(anyList(),anyList(),anyString())).thenReturn(proposal(plan(false)));var selected=send(c,"Ambiguityname Beta",ambiguous);assertEquals("review",selected.path("nutritionTask").path("status").asText(),selected.toString());assertEquals(b.id().toString(),selected.path("nutritionTask").path("clientId").asText());assertEquals(180,selected.path("nutritionTask").path("targets").path("proteinG").asInt());assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM nutrition_target WHERE athlete_id=?",Integer.class,b.id()));
        var noTargets=athlete("Missingneeds Unique");var missing=send(c,"Maak een voedingsschema voor Missingneeds Unique",null);assertTrue(missing.path("answer").asText().contains("caloriedoel"));var completed=send(c,"3000 kcal, 180 g eiwit, kies de rest",missing);assertEquals("review",completed.path("nutritionTask").path("status").asText(),completed.toString());assertEquals(noTargets.id().toString(),completed.path("nutritionTask").path("clientId").asText());
    }
    @Test void backendToolsRecheckScopeConsentAndNeverExposeOtherClientContext()throws Exception{
        var a=athlete("Scopednutrition Unique");targets(a);var c=coach();when(gateway.nutritionTools(anyList(),anyList(),anyString())).thenReturn(new AiCoachGateway.ToolReply(List.of(Map.of("type","function_call","call_id","context","name","get_nutrition_context","arguments",json.writeValueAsString(Map.of("clientId",a.id())))),List.of(new AiCoachGateway.ToolCall("context","get_nutrition_context",json.writeValueAsString(Map.of("clientId",a.id())))),""),proposal(plan(false)));
        var result=send(c,"Maak een voedingsschema voor Scopednutrition Unique",null);assertEquals("review",result.path("nutritionTask").path("status").asText(),result.toString());verify(gateway,times(2)).nutritionTools(anyList(),anyList(),anyString());
        var web=client();login(web,a.email(),"Athlete-test-password-123");assertEquals(403,request(web,"POST","/api/ai-coach/nutrition-chat","{}",csrf(web),false).statusCode());assertEquals(403,request(web,"POST","/api/ai-coach/actions/"+result.path("proposal").path("id").asText()+"/confirm","{}",csrf(web),false).statusCode());
        preferences.save(a::email,new NutritionPreferences.Values("","",false,1));assertThrows(org.springframework.web.server.ResponseStatusException.class,()->actions.confirm(actor(),UUID.fromString(result.path("proposal").path("id").asText())));assertThrows(org.springframework.web.server.ResponseStatusException.class,()->tasks.restore(actor(),UUID.fromString(result.path("conversationId").asText())));
    }
    @Test void invalidCompleteWeekIsRepairedOnceAndProviderFailuresAreNotRetried()throws Exception{
        var a=athlete("Repairnutrition Unique");targets(a);var c=coach();when(gateway.nutritionTools(anyList(),anyList(),anyString())).thenReturn(proposal(Map.of("name","Incomplete","days",List.of())),proposal(plan(false)));var result=send(c,"Maak een voedingsschema voor Repairnutrition Unique",null);assertEquals("review",result.path("nutritionTask").path("status").asText(),result.toString());verify(gateway,times(2)).nutritionTools(anyList(),anyList(),anyString());
        reset(gateway);when(gateway.configured()).thenReturn(true);when(gateway.nutritionTools(anyList(),anyList(),anyString())).thenThrow(new AiCoachFailure("provider_request"));var failed=send(c,"Meer variatie",result);assertEquals("failed",failed.path("nutritionTask").path("status").asText());assertEquals("3000.0",failed.path("nutritionTask").path("targets").path("calories").asText());verify(gateway,times(1)).nutritionTools(anyList(),anyList(),anyString());assertFalse(failed.has("proposal"));
    }
    @Test void duplicateTurnsCostOnceAndStaleRevisionsAndInstructorAccessAreRejected()throws Exception{
        var a=athlete("Idempotentnutrition Unique");targets(a);var c=coach();when(gateway.nutritionTools(anyList(),anyList(),anyString())).thenReturn(proposal(plan(false)));
        var input=Map.of("id",UUID.randomUUID(),"revision",0,"message","Maak nu een voedingsschema voor volgende week","history",List.of(new AiCoachGateway.Turn("user","Ik werk aan een workout voor Idempotentnutrition Unique")));String body=json.writeValueAsString(input);
        var first=request(c,"POST","/api/ai-coach/nutrition-chat",body,csrf(c),false);assertEquals(200,first.statusCode(),first.body());var again=request(c,"POST","/api/ai-coach/nutrition-chat",body,csrf(c),false);assertEquals(first.body(),again.body());verify(gateway,times(1)).nutritionTools(anyList(),anyList(),anyString());assertEquals(1,jdbc.queryForObject("SELECT reserved_cents FROM ai_coach_budget WHERE id=1",Integer.class));
        String id=json.readTree(first.body()).path("conversationId").asText();assertEquals(409,request(c,"POST","/api/ai-coach/nutrition-chat",json.writeValueAsString(Map.of("id",UUID.randomUUID(),"conversationId",id,"revision",0,"message","Meer variatie")),csrf(c),false).statusCode());
        jdbc.update("UPDATE cap_user SET staff_role='INSTRUCTOR' WHERE id=?",actor().id());try{assertEquals(403,request(c,"GET","/api/ai-coach/nutrition-chat/"+id,null,null,false).statusCode());assertThrows(org.springframework.web.server.ResponseStatusException.class,()->tasks.context(actor(),a.id()));}finally{jdbc.update("UPDATE cap_user SET staff_role='OWNER' WHERE id=?",actor().id());}
        assertEquals(200,request(c,"DELETE","/api/ai-coach/nutrition-chat/"+id,null,csrf(c),false).statusCode());assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM ai_nutrition_task WHERE id=?",Integer.class,UUID.fromString(id)));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM ai_coach_action WHERE id=?",Integer.class,UUID.fromString(json.readTree(first.body()).path("proposal").path("id").asText())));
    }
    @Test void revisedDraftDoesNotChangePublishedWeekOrSavedCalculations()throws Exception{
        var a=athlete("Publishednutrition Unique");targets(a);var c=coach();when(gateway.nutritionTools(anyList(),anyList(),anyString())).thenReturn(proposal(plan(false)));
        var first=send(c,"Maak een voedingsschema voor Publishednutrition Unique voor 2026-11-16",null);actions.confirm(actor(),UUID.fromString(first.path("proposal").path("id").asText()));var saved=meals.client(actor()::email,a.id(),LocalDate.of(2026,11,16)).getFirst();meals.publish(actor()::email,saved.id(),new MealPlanController.Version(saved.version()));String published=json.writeValueAsString(meals.self(a::email,saved.weekStart()));
        doReturn(proposal(plan(true))).when(gateway).nutritionTools(anyList(),anyList(),anyString());var second=send(c,"Meer variatie, gebruik 2800 kcal en 170 g eiwit, kies de rest",first);assertEquals("review",second.path("nutritionTask").path("status").asText(),second.toString());assertEquals(2800,second.path("nutritionTask").path("targets").path("calories").asInt());actions.confirm(actor(),UUID.fromString(second.path("proposal").path("id").asText()));
        // Compare the athlete-visible content, ignoring the shared draft version counter.
        assertEquals(json.readTree(published).get(0).path("content"),json.valueToTree(meals.self(a::email,saved.weekStart())).get(0).path("content"));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM meal_plan WHERE athlete_id=?",Integer.class,a.id()));assertEquals(3000.0,jdbc.queryForObject("SELECT calories FROM nutrition_target WHERE athlete_id=?",Double.class,a.id()));
    }
    @Test void selectedClientWorkspaceUsesSavedNeedsWithoutNameAmbiguityOrAllergyQuestion()throws Exception{
        athlete("Workspaceguillaume First");var chosen=athlete("Workspaceguillaume Second");targets(chosen);var c=coach();when(gateway.nutritionTools(anyList(),anyList(),anyString())).thenAnswer(inv->{String context=json.writeValueAsString(inv.getArgument(0));assertTrue(context.contains(chosen.id().toString()));assertTrue(context.contains("3000"));return proposal(plan(false));});
        var input=Map.of("id",UUID.randomUUID(),"revision",0,"athleteId",chosen.id(),"weekStart","2027-01-04","message","Maak een volledig gevarieerd voedingsschema op basis van de opgeslagen berekende behoeften voor de komende week voor Workspaceguillaume.");var response=request(c,"POST","/api/ai-coach/nutrition-chat",json.writeValueAsString(input),csrf(c),false);assertEquals(200,response.statusCode(),response.body());var task=json.readTree(response.body()).path("nutritionTask");assertEquals("review",task.path("status").asText(),response.body());assertEquals(chosen.id().toString(),task.path("clientId").asText());assertEquals("2027-01-04",task.path("weekStart").asText());assertEquals(3000,task.path("targets").path("calories").asInt());
    }
}
