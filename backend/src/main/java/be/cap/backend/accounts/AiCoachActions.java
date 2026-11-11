package be.cap.backend.accounts;

import be.cap.backend.*;
import java.security.Principal;
import java.time.*;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static be.cap.backend.accounts.StaffPolicy.Permission.*;

/** The model proposes data. Only a separately confirmed, actor-owned server proposal can write. */
@Service @Profile("accounts")
public class AiCoachActions {
    public record WorkoutInput(UUID clientId,String date,UUID sessionId,Workout workout){}
    public record AssignInput(UUID clientId,String date,String workoutId){}
    public record MealInput(UUID clientId,LocalDate weekStart,String name,List<MealPlanController.Day> days,Boolean repeatDay,Boolean publish){}
    public record TargetInput(UUID clientId,NutritionController.Targets targets){}
    public record NutritionDraftInput(UUID clientId,LocalDate weekStart,UUID planId,int version,MealPlanController.Content content,NutritionTaskRules.Targets targets){}
    public record Command(String type,JsonNode data,String summary,String expectedState,Integer clientConsentVersion,String clientConsentId){public Command(String type,JsonNode data,String summary){this(type,data,summary,null,null,null);}public Command(String type,JsonNode data,String summary,String expectedState){this(type,data,summary,expectedState,null,null);}}
    private final JdbcTemplate jdbc;private final StaffPolicy policy;private final AccountWorkoutService workouts;private final AccountExerciseService exercises;
    private final NutritionPlanValidator nutritionValidator;private final MealPlanController meals;private final NutritionController nutrition;private final PlanningService planning;private final CoachShiftController shifts;private final AiCoachSettings settings;private final NutritionPreferences preferences;private final AiOwnContext ownContext;private final MeasurementService measurements;
    private final JsonMapper json=JsonMapper.builder().build();
    public AiCoachActions(JdbcTemplate jdbc,StaffPolicy policy,AccountWorkoutService workouts,AccountExerciseService exercises,MealPlanController meals,NutritionController nutrition,PlanningService planning,CoachShiftController shifts,AiCoachSettings settings,NutritionPreferences preferences,AiOwnContext ownContext,MeasurementService measurements,NutritionPlanValidator nutritionValidator){this.nutritionValidator=nutritionValidator;this.jdbc=jdbc;this.policy=policy;this.workouts=workouts;this.exercises=exercises;this.meals=meals;this.nutrition=nutrition;this.planning=planning;this.shifts=shifts;this.settings=settings;this.preferences=preferences;this.ownContext=ownContext;this.measurements=measurements;}
    private ResponseStatusException invalid(String text){return new ResponseStatusException(HttpStatus.BAD_REQUEST,text);}
    private LocalDate today(){return LocalDate.now(ZoneId.of("Europe/Brussels"));}
    private Principal principal(AccountService.Account actor){return actor::email;}
    private String client(AccountService.Account actor,UUID id){if(id==null)throw invalid("Kies een klant.");policy.requireClient(actor,id.toString());var people=jdbc.query("SELECT display_name FROM cap_user WHERE id=? AND role='ATHLETE' AND active=TRUE",(r,i)->r.getString(1),id);if(people.isEmpty())throw invalid("Klant niet meer beschikbaar.");return people.getFirst();}
    private void date(String value){try{if(LocalDate.parse(value).isBefore(today()))throw invalid("Kies vandaag of een toekomstige datum.");}catch(RuntimeException e){throw invalid("Geef een concrete datum op.");}}
    private <T>T read(JsonNode node,Class<T> type){try{return json.treeToValue(node,type);}catch(Exception e){throw invalid("Het actievoorstel bevat ongeldige velden. Vraag een nieuw voorstel.");}}
    private boolean mentionsName(String question,String name){return Arrays.stream(name.toLowerCase(Locale.ROOT).split("\\s+")).filter(word->word.length()>=3).anyMatch(word->java.util.regex.Pattern.compile("(?<![\\p{L}\\p{N}])"+java.util.regex.Pattern.quote(word)+"(?:['’]?s)?(?![\\p{L}\\p{N}])").matcher(question).find());}
    public Map<String,Object> resources(AccountService.Account actor,String question,List<String> names) throws Exception {
        var capabilities=policy.capabilities(actor);var clients=new ArrayList<Map<String,Object>>();
        if(policy.role(actor).equals("OWNER")||policy.role(actor).equals("MANAGER")||policy.role(actor).equals("INSTRUCTOR")){
            var ids=policy.scopedClients(actor);String query=question.toLowerCase(Locale.ROOT);
            for(var person:jdbc.query("SELECT id,display_name FROM cap_user WHERE role='ATHLETE' AND active=TRUE ORDER BY display_name",(r,i)->Map.<String,Object>of("id",r.getObject(1,UUID.class),"name",r.getString(2)))){
                String name=person.get("name").toString();if(ids.contains(person.get("id"))&&mentionsName(query,name)&&clients.size()<20)clients.add(person);
            }
        }
        var coaches=policy.allows(actor,CLASS_MANAGE)||policy.role(actor).equals("OWNER")?jdbc.queryForList("SELECT id,display_name AS name FROM cap_user WHERE role='COACH' AND active=TRUE AND staff_role IN ('OWNER','MANAGER','INSTRUCTOR') ORDER BY display_name LIMIT 20"):List.of();
        var templates=policy.allows(actor,WORKOUT_VIEW)?workouts.list(actor).stream().filter(w->question.toLowerCase(Locale.ROOT).contains(w.name().toLowerCase(Locale.ROOT))).limit(10).map(w->Map.of("id",w.id(),"name",w.name())).toList():List.of();
        return Map.of("todayBrussels",today().toString(),"actor",Map.of("id",actor.id(),"name",actor.name()),"permissions",capabilities,"matchingClients",clients,"coaches",coaches,"matchingSavedWorkouts",templates,"exerciseNames",names,"genericMealFoods",meals.foods(),"originalQuestion",question);
    }
    public Map<String,Object> resourcesForChat(AccountService.Account actor,String conversation,String currentQuestion,List<String> names,boolean shareGoals) throws Exception {
        var resources=new LinkedHashMap<>(resources(actor,conversation,names));
        String query=currentQuestion.toLowerCase(Locale.ROOT);
        boolean nutritionQuestion=java.util.regex.Pattern.compile("voed|maaltijd|eet|eten|calorie|eiwit|macro|allerg|intoler|vermijd|meal|nutrition|diet").matcher(query).find();
        boolean trainingQuestion=java.util.regex.Pattern.compile("train|workout|oefen|kracht|record|squat|bench|deadlift").matcher(query).find();
        boolean bookingQuestion=java.util.regex.Pattern.compile("boek|sessie|planning|afspraak|les|agenda").matcher(query).find();
        boolean measurementQuestion=java.util.regex.Pattern.compile("gewicht|lengte|lichaam|meting|vetpercentage|progress|voortgang|profiel").matcher(query).find();
        boolean general=!nutritionQuestion&&!trainingQuestion&&!bookingQuestion&&!measurementQuestion;
        if(!shareGoals)return resources;
        @SuppressWarnings("unchecked") var clients=(List<Map<String,Object>>)resources.get("matchingClients");
        if(clients.size()!=1)return resources;
        var matched=clients.getFirst();String name=matched.get("name").toString();
        if(!mentionsName(query,name)&&java.util.regex.Pattern.compile("(?iu)(?<![\\p{L}\\p{N}])(ik|mijn|me|my|mine)(?![\\p{L}\\p{N}])").matcher(query).find())return resources;
        UUID id=(UUID)matched.get("id");policy.requireClient(actor,id.toString());var consent=preferences.sharedWithAi(id);if(consent.isEmpty())return resources;resources.put("selectedClientConsentVersion",consent.get().version());
        boolean health=policy.allows(actor,CLIENT_HEALTH),food=health&&policy.allows(actor,NUTRITION_MANAGE)&&(nutritionQuestion||general),training=policy.allows(actor,WORKOUT_VIEW)&&(trainingQuestion||general),bookings=policy.allows(actor,CLASS_VIEW)&&(bookingQuestion||general),profile=health&&(nutritionQuestion||trainingQuestion||measurementQuestion||general),measure=health&&(measurementQuestion||general);
        var person=new AccountService.Account(id,"",name,"ATHLETE");String snapshot=ownContext.read(person,training,food,profile,bookings,food);if(!snapshot.isEmpty()){var selected=(tools.jackson.databind.node.ObjectNode)json.readTree(snapshot);selected.put("scope","Only the uniquely selected, consenting client; bounded snapshot, not all app records.");if(profile)selected.put("profileLimits","This profile contains name, goal and experience. Separate selectedClientProfile may contain age, sex, height and weight; no contact details or exact birth date.");resources.put("selectedClientCoachingContext",selected);}
        if(profile){var profiles=jdbc.query("SELECT values_json FROM account_profile WHERE user_id=?",(r,i)->json.readTree(r.getString(1)),id);if(!profiles.isEmpty()){var source=profiles.getFirst();var details=new LinkedHashMap<String,Object>();for(String field:List.of("sex","heightCm","weightKg")){var value=source.path(field);if(value.isString()&&!value.asText().isBlank())details.put(field,value.asText());else if(value.isNumber())details.put(field,value.asDouble());}try{var born=LocalDate.parse(source.path("birthDate").asText());if(!born.isAfter(today()))details.put("ageYears",Period.between(born,today()).getYears());}catch(java.time.DateTimeException ignored){}if(!details.isEmpty())resources.put("selectedClientProfile",details);}}
        if(measure){var snap=measurements.snapshot(id);resources.put("selectedClientMeasurements",Map.of("profile",snap.profile(),"recent",snap.measurements().stream().limit(5).map(entry->Map.of("recordedAt",Objects.toString(entry.recordedAt(),""),"values",entry.values())).toList()));}
        if(food){var goals=jdbc.query("SELECT calories,protein_g,carbs_g,fat_g,water_ml FROM nutrition_target WHERE athlete_id=?",(r,i)->Map.<String,Object>of("calories",r.getDouble(1),"proteinG",r.getDouble(2),"carbsG",r.getDouble(3),"fatG",r.getDouble(4),"waterMl",r.getDouble(5)),id);if(!goals.isEmpty())resources.put("selectedClientNutritionGoals",Map.of("clientId",id,"name",name,"targets",goals.getFirst()));var value=consent.get();resources.put("selectedClientDietaryInformation",Map.of("clientId",id,"allergies",value.allergies(),"avoidedFoods",value.avoidedFoods()));}
        return resources;
    }
    private Workout checkedWorkout(AccountService.Account actor,Workout input) throws Exception {
        policy.require(actor,WORKOUT_TEMPLATE_MANAGE);if(input==null||input.exercises()==null||input.exercises().isEmpty()||input.exercises().size()>12)throw invalid("Gebruik 1 tot 12 oefeningen.");
        var available=exercises.list(actor).stream().filter(e->!e.archived()).map(ExerciseStore.Exercise::name).toList();
        for(var exercise:input.exercises())if(exercise==null||!available.contains(exercise.name())||!"none".equals(exercise.loadMode())&& !"fixed".equals(exercise.loadMode()))throw invalid("Oefening of gewicht niet herkend. Gebruik een catalogusnaam en geen PR-percentages.");
        var store=new WorkoutStore(new WorkoutStore.State(List.of(),List.of()),state->{},id->false);
        var normalized=store.create(new Workout(null,input.name(),input.description(),input.exercises(),"","",input.category(),input.workoutType()));return new Workout(null,normalized.name(),normalized.description(),normalized.exercises(),"","",normalized.category(),normalized.workoutType());
    }
    private void coachId(UUID id){if(id==null||jdbc.queryForObject("SELECT COUNT(*) FROM cap_user WHERE id=? AND role='COACH' AND active=TRUE AND staff_role IN ('OWNER','MANAGER','INSTRUCTOR')",Integer.class,id)!=1)throw invalid("Kies een actieve coach.");}
    private void upcoming(LocalDate date,LocalTime start,LocalTime end){if(date==null||start==null||end==null||!end.isAfter(start)||!LocalDateTime.of(date,start).isAfter(LocalDateTime.now(ZoneId.of("Europe/Brussels"))))throw invalid("Kies een toekomstige datum en geldige begin- en eindtijd.");}
    private String targetState(UUID client){return jdbc.query("SELECT saved_at FROM nutrition_target WHERE athlete_id=?",(r,i)->r.getTimestamp(1).toInstant().toString(),client).stream().findFirst().orElse("none");}
    private String coachName(UUID id){return jdbc.queryForObject("SELECT display_name FROM cap_user WHERE id=?",String.class,id);}
    private Command normalize(AccountService.Account actor,Command command) throws Exception {
        var data=command.data();if(data==null||!data.isObject())throw invalid("Actiegegevens ontbreken.");
        return switch(command.type()){
            case "save_nutrition_draft"->{
                policy.require(actor,NUTRITION_MANAGE);policy.require(actor,CLIENT_HEALTH);var p=read(data,NutritionDraftInput.class);String who=client(actor,p.clientId());
                if(p.weekStart()==null||p.weekStart().getDayOfWeek()!=DayOfWeek.MONDAY||p.targets()==null)throw invalid("Week of doelen ontbreken.");
                var existing=meals.client(principal(actor),p.clientId(),p.weekStart());
                if(p.planId()==null&&!existing.isEmpty()||p.planId()!=null&&(existing.isEmpty()||!existing.getFirst().id().equals(p.planId())||existing.getFirst().version()!=p.version()))throw new ResponseStatusException(HttpStatus.CONFLICT,"Het voedingsplan is inmiddels gewijzigd. Vraag een nieuw voorstel.");
                var content=meals.valid(p.content());var refs=meals.foods();
                for(var day:content.days()){
                    if(day.meals().size()<3)throw invalid("Elke dag moet complete maaltijden bevatten.");
                    for(var meal:day.meals())for(var food:meal.foods())if(refs.stream().noneMatch(f->f.name().equals(food.name())&&f.preparation().equals(food.preparation())&&f.unit().equals(food.unit())&&f.calories()==food.calories()&&f.proteinG()==food.proteinG()&&f.carbsG()==food.carbsG()&&f.fatG()==food.fatG()))throw invalid("Voedingswaarden komen niet overeen met CAP-referenties.");
                    var total=NutritionPlanValidator.totals(day.meals());if(!nutritionValidator.meets(total,p.targets()))throw invalid("Het voedingsconcept haalt de dagelijkse doelen niet.");
                }
                yield new Command(command.type(),data,"Voedingsconcept voor "+who+" · "+p.weekStart()+" · alleen concept opslaan, niet publiceren");
            }
            case "create_workout"->{var p=read(data,WorkoutInput.class);var workout=checkedWorkout(actor,p.workout());String target="bibliotheek";
                if(p.clientId()!=null){policy.require(actor,WORKOUT_ASSIGN);target=client(actor,p.clientId());date(p.date());target+=" op "+p.date();}
                if(p.sessionId()!=null){policy.require(actor,CLASS_MANAGE);var session=planning.list(actor,today(),today().plusDays(370)).stream().filter(s->s.id().equals(p.sessionId())).findFirst().orElseThrow(()->invalid("Les niet gevonden."));target+="; koppelen aan "+session.title()+" "+session.date();}
                yield new Command(command.type(),json.valueToTree(new WorkoutInput(p.clientId(),p.date(),p.sessionId(),workout)),"Workout ‘"+workout.name()+"’ maken → "+target);
            }
            case "assign_workout"->{policy.require(actor,WORKOUT_ASSIGN);var p=read(data,AssignInput.class);String who=client(actor,p.clientId());date(p.date());var workout=workouts.list(actor).stream().filter(w->w.id().equals(p.workoutId())).findFirst().orElseThrow(()->invalid("Opgeslagen workout niet gevonden."));yield new Command(command.type(),data,"Workout ‘"+workout.name()+"’ toewijzen aan "+who+" op "+p.date());}
            case "create_meal_plan"->{policy.require(actor,NUTRITION_MANAGE);var p=read(data,MealInput.class);String who=client(actor,p.clientId());if(p.weekStart()==null||p.weekStart().getDayOfWeek()!=DayOfWeek.MONDAY)throw invalid("Geef de maandag van de planweek op.");var days=p.days();
                if(Boolean.TRUE.equals(p.repeatDay())&&days!=null&&days.size()==1){var first=days.getFirst();days=java.util.stream.IntStream.range(0,7).mapToObj(i->new MealPlanController.Day(i,first.meals())).toList();}
                var references=meals.foods();var safeDays=new ArrayList<MealPlanController.Day>();if(days==null)throw invalid("Maaltijden ontbreken.");
                for(var day:days){var safeMeals=new ArrayList<MealPlanController.Meal>();for(var meal:day.meals()){var safeFoods=new ArrayList<MealPlanController.Food>();for(var food:meal.foods()){
                    var reference=references.stream().filter(f->f.name().equals(food.name())&&f.preparation().equals(food.preparation())&&f.unit().equals(food.unit())).findFirst().orElseThrow(()->invalid("Voedingsmiddel of bereidingswijze niet in de referentielijst."));safeFoods.add(new MealPlanController.Food(reference.name(),reference.preparation(),reference.unit(),food.amount(),reference.calories(),reference.proteinG(),reference.carbsG(),reference.fatG()));
                }safeMeals.add(new MealPlanController.Meal(meal.name(),safeFoods));}safeDays.add(new MealPlanController.Day(day.day(),safeMeals));}
                var content=meals.valid(new MealPlanController.Content(p.name(),safeDays));if(jdbc.queryForObject("SELECT COUNT(*) FROM meal_plan WHERE athlete_id=? AND week_start=?",Integer.class,p.clientId(),p.weekStart())>0)throw invalid("Er bestaat al een voedingsplan voor deze klant/week. Kies een nieuwe week of bewerk het bestaande plan in Voeding.");
                yield new Command(command.type(),json.valueToTree(new MealInput(p.clientId(),p.weekStart(),content.name(),content.days(),false,p.publish())),"Voedingsplan ‘"+content.name()+"’ voor "+who+" vanaf "+p.weekStart()+(Boolean.TRUE.equals(p.publish())?" · publiceren":" · alleen concept"));
            }
            case "nutrition_targets"->{policy.require(actor,NUTRITION_MANAGE);var p=read(data,TargetInput.class);String who=client(actor,p.clientId());if(p.targets()==null)throw invalid("Dagdoelen ontbreken.");var t=p.targets();for(Double v:Arrays.asList(t.calories(),t.proteinG(),t.carbsG(),t.fatG(),t.waterMl()))if(v==null||!Double.isFinite(v)||v<0||v>100000)throw invalid("Controleer alle vijf voedingsdoelen.");if(t.calories()<1)throw invalid("Caloriedoel ontbreekt.");String state=targetState(p.clientId());if(command.expectedState()!=null&&!command.expectedState().equals(state))throw new ResponseStatusException(HttpStatus.CONFLICT,"De dagdoelen zijn gewijzigd sinds dit voorstel. Vraag een nieuw voorstel.");yield new Command(command.type(),data,"Dagdoelen vervangen voor "+who+": "+t.calories()+" kcal; eiwit "+t.proteinG()+" g; koolhydraten "+t.carbsG()+" g; vet "+t.fatG()+" g; water "+t.waterMl()+" ml",command.expectedState()==null?state:command.expectedState());}
            case "create_lesson"->{policy.require(actor,CLASS_MANAGE);var p=read(data,PlanningService.Input.class);if(p.type()!=null&&p.type().equals("Privé")&&(p.athleteIds()==null||p.athleteIds().isEmpty()))throw invalid("Kies deelnemers voor de privéles.");if(p.type()!=null&&!p.type().equals("Privé")&&p.athleteIds()!=null&&!p.athleteIds().isEmpty())throw invalid("Groepslessen maken schrijft klanten niet automatisch in. Gebruik de boekingen voor inschrijvingen.");coachId(p.coachId());upcoming(LocalDate.parse(p.date()),LocalTime.parse(p.start()),LocalTime.parse(p.end()));if(p.athleteIds()!=null)for(var id:p.athleteIds())client(actor,id);if(p.title()==null||p.title().isBlank()||p.location()==null||p.location().isBlank()||p.capacity()<1||p.capacity()>200||!Set.of("Groep","Privé","Open gym").contains(p.type())||!Set.of(1,4,8).contains(p.repeat()))throw invalid("Vul naam, locatie, capaciteit, lestype en herhaling in.");yield new Command(command.type(),data,"Coach "+coachName(p.coachId())+"; deelnemers: "+(p.athleteIds()==null||p.athleteIds().isEmpty()?"geen automatische inschrijving":p.athleteIds().stream().map(id->client(actor,id)).toList())+"; Les ‘"+p.title()+"’ maken · "+p.date()+" "+p.start()+"–"+p.end()+" · "+p.location()+" · "+p.capacity()+" plaatsen · "+p.repeat()+" week/weken");}
            case "create_shift"->{if(!policy.role(actor).equals("OWNER"))throw new ResponseStatusException(HttpStatus.FORBIDDEN);var p=read(data,CoachShiftController.Input.class);coachId(p.coachId());upcoming(p.date(),p.start(),p.end());yield new Command(command.type(),json.valueToTree(new CoachShiftController.Input(p.coachId(),p.date(),p.start(),p.end(),p.note()==null?"":p.note(),-1)),"Werkuren voor "+coachName(p.coachId())+" maken · "+p.date()+" "+p.start()+"–"+p.end());}
            default->throw invalid("Deze actie wordt niet ondersteund.");
        };
    }
    @Transactional(rollbackFor=Exception.class) public Map<String,Object> stageNutrition(AccountService.Account actor,UUID id,NutritionTaskRules.State state,MealPlanController.Content content) throws Exception {
        var approval=preferences.sharedWithAi(state.clientId).orElseThrow(()->invalid("De AI-toestemming is ingetrokken."));
        if(approval.version()!=state.consentVersion)throw new ResponseStatusException(HttpStatus.CONFLICT,"De voorkeuren zijn gewijzigd.");
        var base=normalize(actor,new Command("save_nutrition_draft",json.valueToTree(new NutritionDraftInput(state.clientId,state.weekStart,state.planId,state.planVersion,content,state.targets)),""));
        var command=new Command(base.type(),base.data(),base.summary(),null,approval.version(),state.clientId.toString());
        jdbc.update("INSERT INTO ai_coach_action(id,user_id,payload) VALUES(?,?,?)",id,actor.id(),json.writeValueAsString(List.of(command)));return Map.of("id",id);
    }
    @Transactional(rollbackFor=Exception.class) public Map<String,Object> stage(AccountService.Account actor,UUID id,String output,Map<String,Object> resources) throws Exception {
        var root=json.readTree(output);String answer=root.path("answer").asText();if(answer.isBlank()||answer.length()>4000||!root.path("actions").isArray()||root.path("actions").size()>5)throw invalid("Het actievoorstel is onvolledig. Vraag een korter voorstel.");
        if(root.path("actions").isEmpty())return Map.of("answer",answer);
        for(var item:root.path("actions"))if(item.path("type").asText().equals("save_nutrition_draft"))throw invalid("Gebruik het voedingsgesprek voor een gecontroleerd voedingsconcept.");
        var commands=new ArrayList<Command>();
        @SuppressWarnings("unchecked")var clients=(List<Map<String,Object>>)resources.get("matchingClients");var allowed=clients.stream().map(c->c.get("id").toString()).toList();
        for(var item:root.path("actions")){var command=normalize(actor,new Command(item.path("type").asText(),item.path("data"),""));var target=command.data().path("clientId");if(target.isString()&&!target.asText().isBlank()&&!allowed.contains(target.asText()))throw invalid("De klant is niet eenduidig gevonden. Geef de volledige naam op.");if(command.type().equals("create_lesson"))for(var targetId:command.data().path("athleteIds"))if(!allowed.contains(targetId.asText()))throw invalid("De lesdeelnemer is niet eenduidig gevonden.");if(target.isString()&&!target.asText().isBlank()){
            var chosen=clients.stream().filter(c->c.get("id").toString().equals(target.asText())).findFirst().orElseThrow();String name=chosen.get("name").toString().toLowerCase(Locale.ROOT),first=name.split("\\s+")[0];String question=resources.get("originalQuestion").toString().toLowerCase(Locale.ROOT);
            if(!question.contains(name)&&clients.stream().filter(c->c.get("name").toString().toLowerCase(Locale.ROOT).split("\\s+")[0].equals(first)).count()>1)throw invalid("Meerdere klanten met deze voornaam. Geef de volledige naam op.");
        }
        if(clients.size()==1&&(resources.containsKey("selectedClientCoachingContext")||resources.containsKey("selectedClientDietaryInformation"))){UUID selected=UUID.fromString(clients.getFirst().get("id").toString());var approval=preferences.sharedWithAi(selected).orElseThrow(()->invalid("De klant heeft AI-toestemming ingetrokken. Vraag een nieuw voorstel."));if(!Objects.equals(resources.get("selectedClientConsentVersion"),approval.version()))throw new ResponseStatusException(HttpStatus.CONFLICT,"De voorkeuren van de klant zijn gewijzigd. Vraag een nieuw voorstel.");command=new Command(command.type(),command.data(),command.summary(),command.expectedState(),approval.version(),selected.toString());}
        commands.add(command);}
        jdbc.update("DELETE FROM ai_coach_action WHERE expires_at<CURRENT_TIMESTAMP AND result IS NULL");
        jdbc.update("INSERT INTO ai_coach_action(id,user_id,payload) VALUES(?,?,?)",id,actor.id(),json.writeValueAsString(commands));
        return Map.of("answer",answer,"proposal",Map.of("id",id,"actions",commands,"expiresInHours",24));
    }
    @Transactional(rollbackFor=Exception.class) public Map<String,Object> confirm(AccountService.Account actor,UUID id) throws Exception {
        jdbc.query("SELECT id FROM cap_user WHERE id=? FOR UPDATE",(r,i)->r.getObject(1),actor.id());
        var taskRows=jdbc.queryForList("SELECT processing_id FROM ai_nutrition_task WHERE user_id=? AND payload::jsonb->>'proposalId'=? FOR UPDATE",actor.id(),id.toString());
        if(taskRows.stream().anyMatch(r->r.get("processing_id")!=null))throw new ResponseStatusException(HttpStatus.CONFLICT,"Er wordt een nieuwe versie voorbereid. Wacht op het nieuwste voorstel.");
        var rows=jdbc.queryForList("SELECT payload,result,expires_at>CURRENT_TIMESTAMP AS valid FROM ai_coach_action WHERE id=? AND user_id=? FOR UPDATE",id,actor.id());if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Voorstel niet gevonden.");var row=rows.getFirst();
        if(row.get("result")!=null)return json.readValue(row.get("result").toString(),new tools.jackson.core.type.TypeReference<Map<String,Object>>(){});
        var prefs=settings.read(actor).values();if(!prefs.consent()||!prefs.coachActions()||!Boolean.TRUE.equals(row.get("valid")))throw invalid("Toestemming is ingetrokken of het voorstel is verlopen. Vraag een nieuw voorstel.");
        var commands=json.readValue(row.get("payload").toString(),Command[].class);
        var clientLocks=new java.util.TreeSet<String>();for(var c:commands){String client=c.data().path("clientId").asText();if(!client.isBlank()&&!client.equals("null"))clientLocks.add(client);if(c.clientConsentId()!=null)clientLocks.add(c.clientConsentId());}for(String clientId:clientLocks)jdbc.query("SELECT id FROM cap_user WHERE id=? FOR UPDATE",(r,i)->r.getObject(1),UUID.fromString(clientId));
        var results=new ArrayList<String>();
        for(var stored:commands){if(stored.clientConsentVersion()!=null){UUID idClient=UUID.fromString(stored.clientConsentId()==null?stored.data().path("clientId").asText():stored.clientConsentId());var approval=preferences.sharedWithAi(idClient).orElseThrow(()->invalid("De klant heeft AI-toestemming ingetrokken. Vraag een nieuw voorstel."));if(approval.version()!=stored.clientConsentVersion())throw new ResponseStatusException(HttpStatus.CONFLICT,"De voorkeuren van de klant zijn gewijzigd. Vraag een nieuw voorstel.");}var c=normalize(actor,stored);if(!json.writeValueAsString(c.data()).equals(json.writeValueAsString(stored.data())))throw new ResponseStatusException(HttpStatus.CONFLICT,"De gegevens zijn gewijzigd. Vraag een nieuw voorstel.");var d=c.data();
            switch(c.type()){
                case "save_nutrition_draft"->{var p=read(d,NutritionDraftInput.class);var saved=meals.save(principal(actor),new MealPlanController.Input(p.planId(),p.clientId(),p.weekStart(),p.version(),p.content()));
                    jdbc.update("UPDATE ai_nutrition_task SET payload=jsonb_set(jsonb_set(jsonb_set(payload::jsonb,'{status}','\"saved\"'),'{planId}',to_jsonb(?::text)),'{planVersion}',to_jsonb(?::integer))::text WHERE user_id=? AND payload::jsonb->>'proposalId'=?",saved.id().toString(),saved.version(),actor.id(),id.toString());
                }
                case "create_workout"->{var p=read(d,WorkoutInput.class);var w=workouts.save(null,new Workout(null,p.workout().name(),p.workout().description(),p.workout().exercises(),p.clientId()==null?"":p.clientId().toString(),p.date(),p.workout().category(),p.workout().workoutType()),null,actor);if(p.sessionId()!=null)workouts.attach(p.sessionId(),w.id(),actor);}
                case "assign_workout"->{var p=read(d,AssignInput.class);workouts.assign(p.workoutId(),p.clientId().toString(),p.date(),null,actor);}
                case "create_meal_plan"->{var p=read(d,MealInput.class);var plan=meals.save(principal(actor),new MealPlanController.Input(null,p.clientId(),p.weekStart(),0,new MealPlanController.Content(p.name(),p.days())));if(Boolean.TRUE.equals(p.publish()))meals.publish(principal(actor),plan.id(),new MealPlanController.Version(plan.version()));}
                case "nutrition_targets"->{var p=read(d,TargetInput.class);nutrition.save(principal(actor),p.clientId(),new NutritionController.Input(p.targets(),"handmatig"));}
                case "create_lesson"->planning.save(null,read(d,PlanningService.Input.class),actor);
                case "create_shift"->shifts.save(principal(actor),UUID.randomUUID(),read(d,CoachShiftController.Input.class));
                default->throw invalid("Onbekende actie.");
            }results.add(c.summary());
        }
        String receipt=Arrays.stream(commands).allMatch(c->c.type().equals("save_nutrition_draft"))?"Voedingsconcept opgeslagen. Nog niet gepubliceerd; de klant ziet deze nieuwe versie niet.":"Bevestigd en opgeslagen in CAP.";var result=Map.<String,Object>of("answer",receipt,"completed",results);jdbc.update("UPDATE ai_coach_action SET result=? WHERE id=? AND user_id=?",json.writeValueAsString(result),id,actor.id());policy.audit(actor,"AI_ACTION_CONFIRM",id.toString(),commands.length+" confirmed actions");return result;
    }
}
