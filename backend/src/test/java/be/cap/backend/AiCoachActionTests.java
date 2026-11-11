package be.cap.backend;

import be.cap.backend.accounts.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@TestPropertySource(properties={"cap.ai-coach.enabled=true","cap.ai-coach.testers=coach@example.test"})
class AiCoachActionTests extends AccountApiTests {
    @org.springframework.test.context.DynamicPropertySource static void actionDatabase(org.springframework.test.context.DynamicPropertyRegistry registry){PostgresTestDatabase.configure(registry,"ai_actions");}
    @Autowired AiCoachActions actions;@Autowired AiCoachSettings settings;@Autowired AccountService accounts;@Autowired AccountExerciseService exercises;@Autowired NutritionPreferences preferences;
    @MockitoBean AiCoachGateway gateway;
    @BeforeEach void clearActions(){jdbc.update("DELETE FROM ai_coach_action");jdbc.update("DELETE FROM ai_coach_settings");when(gateway.configured()).thenReturn(true);}
    AccountService.Account actor(){return accounts.account("coach@example.test");}
    AccountService.Account athlete(String name){return accounts.accept(accounts.invite(UUID.randomUUID()+"@example.test",actor().email()).token(),name,"Athlete-test-password-123");}
    void enable(){settings.save(()->actor().email(),new AiCoachSettings.View(0,new AiCoachSettings.Values(true,false,false,false,false,false,true)));}
    String tomorrow(){return LocalDate.now(ZoneId.of("Europe/Brussels")).plusDays(1).toString();}
    Map<String,Object> workout(UUID client) throws Exception {return Map.of("type","create_workout","data",Map.of("clientId",client,"date",tomorrow(),"workout",Map.of("name","AI reviewed workout","description","","category","","workoutType","full-body","exercises",List.of(Map.of("name",exercises.list(actor()).stream().filter(e->!e.archived()).findFirst().orElseThrow().name(),"sets",3,"reps",8,"loadMode","none","rir",2,"restSeconds",90)))));}
    UUID stage(String question,List<Map<String,Object>> commands) throws Exception {UUID id=UUID.randomUUID();actions.stage(actor(),id,json.writeValueAsString(Map.of("answer","Controleer dit voorstel.","actions",commands)),actions.resources(actor(),question,List.of()));return id;}
    @Test void olderSavedProposalsRemainReadable(){var old=json.readValue("[{\"type\":\"create_workout\",\"data\":{},\"summary\":\"Oud voorstel\",\"expectedState\":null}]",AiCoachActions.Command[].class);assertNull(old[0].clientConsentVersion());}
    @Test void allCreationActionsAreReviewedAtomicAndIdempotent() throws Exception {
        enable();var client=athlete("Action Client Unique");LocalDate week=LocalDate.now(ZoneId.of("Europe/Brussels")).with(DayOfWeek.MONDAY).plusWeeks(1);
        var food=new MealPlanController.Food("Rijst","droog","g",100,0,0,0,0);var day=new MealPlanController.Day(0,List.of(new MealPlanController.Meal("Lunch",List.of(food))));
        var meal=Map.<String,Object>of("type","create_meal_plan","data",Map.of("clientId",client.id(),"weekStart",week,"name","AI meal week","days",List.of(day),"repeatDay",true,"publish",true));
        var target=Map.<String,Object>of("type","nutrition_targets","data",Map.of("clientId",client.id(),"targets",new NutritionController.Targets(2100.0,150.0,230.0,65.0,2000.0)));
        var lesson=Map.<String,Object>of("type","create_lesson","data",new PlanningService.Input("AI reviewed lesson",tomorrow(),"15:00","16:00",actor().id(),"CAP",10,"Groep","","",List.of(),1));
        var shift=Map.<String,Object>of("type","create_shift","data",new CoachShiftController.Input(actor().id(),LocalDate.parse(tomorrow()),LocalTime.of(10,0),LocalTime.of(11,0),"",-1));
        String before=jdbc.queryForObject("SELECT payload FROM workout_state WHERE id=1",String.class);UUID id=stage(client.name(),List.of(workout(client.id()),meal,target,lesson,shift));assertEquals(before,jdbc.queryForObject("SELECT payload FROM workout_state WHERE id=1",String.class));
        var web=client();login(web,actor().email(),"Coach-test-password-123");var result=request(web,"POST","/api/ai-coach/actions/"+id+"/confirm","{}",csrf(web),false);assertEquals(200,result.statusCode(),result.body());assertEquals(5,json.readTree(result.body()).path("completed").size());
        String after=jdbc.queryForObject("SELECT payload FROM workout_state WHERE id=1",String.class);assertTrue(after.contains("\"rir\":2"));assertTrue(after.contains("AI reviewed workout"));assertNotEquals(before,after);
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM meal_plan WHERE athlete_id=? AND published_payload IS NOT NULL",Integer.class,client.id()));
        String published=jdbc.queryForObject("SELECT published_payload FROM meal_plan WHERE athlete_id=?",String.class,client.id());assertTrue(published.contains("348.6"));assertEquals(150.0,jdbc.queryForObject("SELECT protein_g FROM nutrition_target WHERE athlete_id=?",Double.class,client.id()));
        assertEquals(result.body(),request(web,"POST","/api/ai-coach/actions/"+id+"/confirm","{}",csrf(web),false).body());assertEquals(after,jdbc.queryForObject("SELECT payload FROM workout_state WHERE id=1",String.class));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM club_session WHERE title='AI reviewed lesson'",Integer.class));
    }
    @Test void laterFailureRollsBackEarlierAction() throws Exception {
        enable();var client=athlete("Rollback Client Unique");var shift=new CoachShiftController.Input(actor().id(),LocalDate.parse(tomorrow()).plusDays(3),LocalTime.of(10,0),LocalTime.of(11,0),"",-1);
        var existing=new CoachShiftController.Input(actor().id(),shift.date(),shift.start(),shift.end(),"Existing",-1);
        var web=client();login(web,actor().email(),"Coach-test-password-123");assertEquals(200,request(web,"PUT","/api/coach-shifts/"+UUID.randomUUID(),json.writeValueAsString(existing),csrf(web),false).statusCode());
        UUID id=stage(client.name(),List.of(workout(client.id()),Map.of("type","create_shift","data",shift)));String before=jdbc.queryForObject("SELECT payload FROM workout_state WHERE id=1",String.class);
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->actions.confirm(actor(),id));assertEquals(before,jdbc.queryForObject("SELECT payload FROM workout_state WHERE id=1",String.class));assertNull(jdbc.queryForObject("SELECT result FROM ai_coach_action WHERE id=?",String.class,id));
    }
    @Test void revokedConsentChangedPermissionsAndWrongOwnerCannotExecute() throws Exception {
        enable();var client=athlete("Access Review Unique");UUID id=stage(client.name(),List.of(workout(client.id())));String before=jdbc.queryForObject("SELECT payload FROM workout_state WHERE id=1",String.class);
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->actions.confirm(client,id));
        settings.save(()->actor().email(),new AiCoachSettings.View(1,new AiCoachSettings.Values(false,false,false,false,false,false,false)));assertThrows(org.springframework.web.server.ResponseStatusException.class,()->actions.confirm(actor(),id));
        settings.save(()->actor().email(),new AiCoachSettings.View(2,new AiCoachSettings.Values(true,false,false,false,false,false,true)));jdbc.update("UPDATE cap_user SET staff_role='INSTRUCTOR' WHERE id=?",actor().id());
        try{assertThrows(org.springframework.web.server.ResponseStatusException.class,()->actions.confirm(actor(),id));}finally{jdbc.update("UPDATE cap_user SET staff_role='OWNER' WHERE id=?",actor().id());}
        jdbc.update("UPDATE ai_coach_action SET expires_at=CURRENT_TIMESTAMP-INTERVAL '1 minute' WHERE id=?",id);assertThrows(org.springframework.web.server.ResponseStatusException.class,()->actions.confirm(actor(),id));assertEquals(before,jdbc.queryForObject("SELECT payload FROM workout_state WHERE id=1",String.class));
    }
    @Test void changedTargetsAndAmbiguousNamesRequireNewReview() throws Exception {
        enable();var one=athlete("Guillaume One "+UUID.randomUUID());var two=athlete("Guillaume Two "+UUID.randomUUID());
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->stage("Guillaume",List.of(workout(one.id()))));
        var target=Map.<String,Object>of("type","nutrition_targets","data",Map.of("clientId",one.id(),"targets",new NutritionController.Targets(2000.0,150.0,200.0,60.0,2000.0)));UUID id=stage(one.name(),List.of(target));
        jdbc.update("INSERT INTO nutrition_target(athlete_id,coach_id,calories,protein_g,carbs_g,fat_g,water_ml,origin,saved_at) VALUES(?,?,2200,100,200,70,2000,'handmatig',CURRENT_TIMESTAMP)",one.id(),actor().id());
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->actions.confirm(actor(),id));assertEquals(100.0,jdbc.queryForObject("SELECT protein_g FROM nutrition_target WHERE athlete_id=?",Double.class,one.id()));
    }
    @Test void settingsPersistWithVersionAndActionProviderReceivesOnlyOperationalRoster() throws Exception {
        var client=athlete("Settings Client Unique");enable();assertTrue(settings.get(()->actor().email()).values().coachActions());assertFalse(settings.get(()->client.email()).values().consent());
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->settings.save(()->actor().email(),new AiCoachSettings.View(0,new AiCoachSettings.Values(false,false,false,false,false,false,false))));
        when(gateway.propose(anyList(),anyMap(),anyString())).thenReturn("{\"answer\":\"Welke datum?\",\"actions\":[]}");var web=client();login(web,actor().email(),"Coach-test-password-123");
        var response=request(web,"POST","/api/ai-coach",json.writeValueAsString(Map.of("id",UUID.randomUUID(),"message","Workout voor "+client.name(),"consent",true,"mode","auto")),csrf(web),false);assertEquals(200,response.statusCode(),response.body());verify(gateway).propose(anyList(),anyMap(),anyString());verify(gateway,never()).draft(anyList(),anyList());
        var resources=actions.resources(actor(),client.name(),List.of());assertTrue(resources.toString().contains(client.name()));assertFalse(resources.toString().contains(client.email()));assertFalse(resources.toString().contains("password"));
    }
    @Test void chatSharesGoalsOnlyForOneNamedClientWithExplicitOptInAndPermission() throws Exception {
        var first=athlete("Mira Zorpex");var second=athlete("Mira Qevlur");
        jdbc.update("INSERT INTO account_profile(user_id,values_json) VALUES(?,?)",first.id(),json.writeValueAsString(new ProfileController.Values(first.name(),"1990-01-01","female",180.0,80.0,"fitness","beginner","PRIVATE_PHONE")));
        jdbc.update("INSERT INTO nutrition_target(athlete_id,coach_id,calories,protein_g,carbs_g,fat_g,water_ml,origin,saved_at) VALUES(?,?,2100,123,230,60,1900,'handmatig',CURRENT_TIMESTAMP)",first.id(),actor().id());
        jdbc.update("INSERT INTO nutrition_target(athlete_id,coach_id,calories,protein_g,carbs_g,fat_g,water_ml,origin,saved_at) VALUES(?,?,2800,170,300,80,2500,'handmatig',CURRENT_TIMESTAMP)",second.id(),actor().id());
        String question="maak een voedingschema voor Zorpex";
        assertFalse(actions.resourcesForChat(actor(),question,question,List.of(),false).containsKey("selectedClientNutritionGoals"));
        assertFalse(actions.resourcesForChat(actor(),question,question,List.of(),true).containsKey("selectedClientNutritionGoals"));
        preferences.save(()->first.email(),new NutritionPreferences.Values("pinda","vis",false,0));assertFalse(actions.resourcesForChat(actor(),question,question,List.of(),true).containsKey("selectedClientDietaryInformation"));
        preferences.save(()->first.email(),new NutritionPreferences.Values("pinda","vis",true,1));var allowed=actions.resourcesForChat(actor(),question,question,List.of(),true);String shared=allowed.get("selectedClientNutritionGoals").toString();assertTrue(shared.contains("2100.0"));assertTrue(shared.contains("123.0"));assertFalse(shared.contains("2800.0"));assertFalse(allowed.toString().contains(first.email()));String dietary=allowed.get("selectedClientDietaryInformation").toString();assertTrue(dietary.contains("pinda"));assertTrue(dietary.contains("vis"));
        assertTrue(allowed.get("selectedClientProfile").toString().contains("heightCm"));assertTrue(allowed.get("selectedClientProfile").toString().contains("ageYears"));assertFalse(allowed.toString().contains("1990-01-01"));assertFalse(allowed.toString().contains("PRIVATE_PHONE"));
        assertTrue(actions.resourcesForChat(actor(),"Wat zijn Zorpexs allergieën?","Wat zijn Zorpexs allergieën?",List.of(),true).containsKey("selectedClientDietaryInformation"));
        assertTrue(actions.resourcesForChat(actor(),question,"maak een voedingschema",List.of(),true).containsKey("selectedClientNutritionGoals"));assertFalse(actions.resourcesForChat(actor(),question,"wat zijn mijn voedingsdoelen?",List.of(),true).containsKey("selectedClientNutritionGoals"));
        assertFalse(actions.resourcesForChat(actor(),question,question,List.of(),false).containsKey("selectedClientDietaryInformation"));
        String trainingQuestion="maak workout voor Zorpex";assertTrue(actions.resourcesForChat(actor(),trainingQuestion,trainingQuestion,List.of(),true).containsKey("selectedClientCoachingContext"));
        settings.save(()->actor().email(),new AiCoachSettings.View(0,new AiCoachSettings.Values(true,false,false,false,false,false,true)));
        when(gateway.propose(anyList(),anyMap(),anyString())).thenReturn("{\"answer\":\"Controleer het concept.\",\"actions\":[]}");var web=client();login(web,actor().email(),"Coach-test-password-123");
        assertEquals(200,request(web,"POST","/api/ai-coach",json.writeValueAsString(Map.of("id",UUID.randomUUID(),"message",question,"consent",true,"mode","auto")),csrf(web),false).statusCode());
        @SuppressWarnings("unchecked") var captor=org.mockito.ArgumentCaptor.forClass(Map.class);verify(gateway).propose(anyList(),captor.capture(),anyString());assertTrue(captor.getValue().toString().contains("selectedClientNutritionGoals"));
        jdbc.update("INSERT INTO instructor_client(instructor_id,client_id,granted_by,granted_at) VALUES(?,?,?,CURRENT_TIMESTAMP)",actor().id(),first.id(),actor().id());jdbc.update("UPDATE cap_user SET staff_role='INSTRUCTOR' WHERE id=?",actor().id());try{var limited=actions.resourcesForChat(actor(),question,question,List.of(),true);assertFalse(limited.containsKey("selectedClientNutritionGoals"));assertFalse(limited.containsKey("selectedClientProfile"));}finally{jdbc.update("UPDATE cap_user SET staff_role='OWNER' WHERE id=?",actor().id());}
        preferences.save(()->first.email(),new NutritionPreferences.Values("pinda","vis",false,2));assertFalse(actions.resourcesForChat(actor(),question,question,List.of(),true).containsKey("selectedClientDietaryInformation"));assertFalse(actions.resourcesForChat(actor(),question,question,List.of(),true).containsKey("selectedClientCoachingContext"));
        assertFalse(actions.resourcesForChat(actor(),"voedingsplan voor Mira","voedingsplan voor Mira",List.of(),true).containsKey("selectedClientNutritionGoals"));
        assertFalse(actions.resourcesForChat(actor(),"Zorpex", "maak workout voor Zorpex",List.of(),true).containsKey("selectedClientNutritionGoals"));
        assertFalse(actions.resourcesForChat(actor(),question,"maak een voedingschema",List.of(),true).containsKey("selectedClientNutritionGoals"));
        jdbc.update("UPDATE cap_user SET staff_role='INSTRUCTOR' WHERE id=?",actor().id());try{assertFalse(actions.resourcesForChat(actor(),question,question,List.of(),true).containsKey("selectedClientNutritionGoals"));}finally{jdbc.update("UPDATE cap_user SET staff_role='OWNER' WHERE id=?",actor().id());}
    }
    @Test void revokingAthleteApprovalPreventsSavingProposalBasedOnApprovedData() throws Exception {
        enable();var person=athlete("Zelora Mavrix");preferences.save(()->person.email(),new NutritionPreferences.Values("","",true,0));String question="maak workout voor Zelora";
        var resources=actions.resourcesForChat(actor(),question,question,List.of(),true);assertTrue(resources.containsKey("selectedClientCoachingContext"));UUID id=UUID.randomUUID();actions.stage(actor(),id,json.writeValueAsString(Map.of("answer","Controleer","actions",List.of(workout(person.id())))),resources);
        preferences.save(()->person.email(),new NutritionPreferences.Values("","",false,1));assertThrows(org.springframework.web.server.ResponseStatusException.class,()->actions.confirm(actor(),id));
    }
}
