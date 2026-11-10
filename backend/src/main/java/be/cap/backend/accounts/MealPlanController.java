package be.cap.backend.accounts;

import java.security.Principal;
import java.time.*;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

@RestController @Profile("accounts") @RequestMapping("/api/meal-plans") @Transactional
public class MealPlanController {
    public record Food(String name,String preparation,String unit,double amount,double calories,double proteinG,double carbsG,double fatG){}
    public record Meal(String name,List<Food> foods,String instructions){public Meal(String name,List<Food> foods){this(name,foods,"");}}
    public record Day(int day,List<Meal> meals){}
    public record Content(String name,List<Day> days){}
    public record Input(UUID id,UUID athleteId,LocalDate weekStart,int version,Content content){}
    public record Version(int version){}
    public record ConsumeInput(int day,int meal,String diaryMeal){}
    public record Plan(UUID id,UUID athleteId,LocalDate weekStart,int version,Content content,boolean published,String publishedAt){}
    private final JdbcTemplate jdbc;private final AccountService accounts;private final StaffPolicy policy;private final FoodDiaryController diary;
    private final JsonMapper json=JsonMapper.builder().build();
    public MealPlanController(JdbcTemplate jdbc,AccountService accounts,StaffPolicy policy,FoodDiaryController diary){this.jdbc=jdbc;this.accounts=accounts;this.policy=policy;this.diary=diary;}
    private AccountService.Account user(Principal p){return accounts.account(p.getName());}
    private ResponseStatusException error(HttpStatus code,String text){return new ResponseStatusException(code,text);}
    private void coach(AccountService.Account actor){policy.require(actor,StaffPolicy.Permission.NUTRITION_MANAGE);}
    private void week(LocalDate date){if(date==null||date.getDayOfWeek()!=DayOfWeek.MONDAY||date.getYear()<2000||date.getYear()>2100)throw error(HttpStatus.BAD_REQUEST,"Kies een geldige week (maandag).");}
    private String text(String value,int max){if(value==null||value.isBlank()||value.length()>max)throw error(HttpStatus.BAD_REQUEST,"Controleer de namen en bereidingswijze.");return value.trim();}
    private void number(double value,double max,boolean positive){if(!Double.isFinite(value)||value<0||value>max||(positive&&value==0))throw error(HttpStatus.BAD_REQUEST,"Controleer porties en voedingswaarden.");}
    Content valid(Content input){
        if(input==null||input.days()==null||input.days().size()!=7)throw error(HttpStatus.BAD_REQUEST,"Een weekplan bevat zeven dagen.");
        var days=new ArrayList<Day>();var seen=new HashSet<Integer>();int foods=0;
        for(var day:input.days()){
            if(day==null||day.day()<0||day.day()>6||!seen.add(day.day())||day.meals()==null||day.meals().size()>6)throw error(HttpStatus.BAD_REQUEST,"Controleer de dagen en maaltijden.");
            var meals=new ArrayList<Meal>();for(var meal:day.meals()){
                if(meal==null||meal.foods()==null||meal.foods().isEmpty()||meal.foods().size()>12)throw error(HttpStatus.BAD_REQUEST,"Voeg per maaltijd 1 tot 12 voedingsmiddelen toe.");
                var items=new ArrayList<Food>();for(var f:meal.foods()){
                    if(f==null||!Set.of("g","ml","stuk").contains(f.unit()==null?"":f.unit()))throw error(HttpStatus.BAD_REQUEST,"Gebruik gram, ml of stuks.");
                    number(f.amount(),5000,true);number(f.calories(),1000,false);number(f.proteinG(),100,false);number(f.carbsG(),100,false);number(f.fatG(),100,false);
                    items.add(new Food(text(f.name(),80),text(f.preparation(),60),f.unit(),f.amount(),f.calories(),f.proteinG(),f.carbsG(),f.fatG()));foods++;
                }String instructions=meal.instructions()==null?"":meal.instructions().trim();if(instructions.length()>1000)throw error(HttpStatus.BAD_REQUEST,"Bereidingsinstructies mogen maximaal 1000 tekens bevatten.");meals.add(new Meal(text(meal.name(),60),List.copyOf(items),instructions));
            }days.add(new Day(day.day(),List.copyOf(meals)));
        }
        if(foods>168)throw error(HttpStatus.BAD_REQUEST,"Gebruik maximaal 168 ingrediënten per weekplan.");
        days.sort(Comparator.comparingInt(Day::day));return new Content(text(input.name(),100),List.copyOf(days));
    }
    private Plan row(java.sql.ResultSet r,boolean published) throws java.sql.SQLException {
        String payload=r.getString(published?"published_payload":"payload");return new Plan(r.getObject("id",UUID.class),r.getObject("athlete_id",UUID.class),r.getDate("week_start")==null?null:r.getDate("week_start").toLocalDate(),r.getInt("version"),json.readValue(payload,Content.class),r.getString("published_payload")!=null,r.getTimestamp("published_at")==null?null:r.getTimestamp("published_at").toInstant().toString());
    }
    @GetMapping("/foods") public List<Food> foods(){try(var stream=getClass().getResourceAsStream("/generic-meal-foods.json")){return List.of(json.readValue(stream,Food[].class));}catch(Exception e){throw error(HttpStatus.SERVICE_UNAVAILABLE,"De algemene voedingslijst kon niet laden.");}}
    @GetMapping("/templates") public List<Plan> templates(Principal p){coach(user(p));return jdbc.query("SELECT * FROM meal_plan WHERE athlete_id IS NULL ORDER BY updated_at DESC LIMIT 100",(r,i)->row(r,false));}
    @GetMapping public List<Plan> client(Principal p,@RequestParam UUID athleteId,@RequestParam LocalDate weekStart){var actor=user(p);coach(actor);policy.requireClient(actor,athleteId.toString());week(weekStart);return jdbc.query("SELECT * FROM meal_plan WHERE athlete_id=? AND week_start=?",(r,i)->row(r,false),athleteId,weekStart);}
    @GetMapping("/{id}/published") public Plan published(Principal p,@PathVariable UUID id){var actor=user(p);coach(actor);var plans=jdbc.query("SELECT * FROM meal_plan WHERE id=? AND athlete_id IS NOT NULL AND published_payload IS NOT NULL",(r,i)->row(r,true),id);if(plans.isEmpty())throw error(HttpStatus.NOT_FOUND,"Geen gepubliceerd plan gevonden.");var plan=plans.getFirst();policy.requireClient(actor,plan.athleteId().toString());return plan;}
    @GetMapping("/self") public List<Plan> self(Principal p,@RequestParam LocalDate weekStart){week(weekStart);return ownPublished(user(p).id(),weekStart);}
    List<Plan> ownPublished(UUID id,LocalDate week){return jdbc.query("SELECT * FROM meal_plan WHERE athlete_id=? AND week_start=? AND published_payload IS NOT NULL",(r,i)->row(r,true),id,week);}
    private UUID diaryId(UUID athlete,Plan plan,int day,int meal){return UUID.nameUUIDFromBytes(("cap-planned-meal:"+athlete+":"+plan.id()+":"+plan.publishedAt()+":"+day+":"+meal).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    @GetMapping("/self/consumed") public Map<String,Boolean> consumed(Principal p,@RequestParam LocalDate weekStart){
        var athlete=user(p);if(!athlete.role().equals("ATHLETE"))throw error(HttpStatus.FORBIDDEN,"Alleen klanten kunnen hun maaltijd registreren.");week(weekStart);var keys=new LinkedHashMap<UUID,String>();
        for(var plan:ownPublished(athlete.id(),weekStart))for(var day:plan.content().days())for(int i=0;i<day.meals().size();i++)keys.put(diaryId(athlete.id(),plan,day.day(),i),day.day()+":"+i);
        var result=new LinkedHashMap<String,Boolean>();keys.values().forEach(key->result.put(key,false));if(keys.isEmpty())return result;
        var params=new ArrayList<Object>();params.add(athlete.id());params.addAll(keys.keySet());String markers=String.join(",",Collections.nCopies(keys.size(),"?"));
        jdbc.query("SELECT id FROM food_diary_entry WHERE athlete_id=? AND id IN ("+markers+")",(r,i)->r.getObject(1,UUID.class),params.toArray()).forEach(id->result.put(keys.get(id),true));return result;
    }
    @PostMapping("/self/{id}/consume") public ResponseEntity<FoodDiaryController.Entry> consume(Principal p,@PathVariable UUID id,@RequestBody ConsumeInput input){
        var athlete=user(p);if(!athlete.role().equals("ATHLETE"))throw error(HttpStatus.FORBIDDEN,"Alleen klanten kunnen hun maaltijd registreren.");
        var plans=jdbc.query("SELECT * FROM meal_plan WHERE id=? AND athlete_id=? AND published_payload IS NOT NULL FOR UPDATE",(r,i)->row(r,true),id,athlete.id());if(plans.isEmpty())throw error(HttpStatus.NOT_FOUND,"Gepubliceerd plan niet gevonden.");var plan=plans.getFirst();
        if(input==null||input.day()<0||input.day()>6||input.meal()<0||!Set.of("breakfast","snack","lunch","dinner","bedtime","pre","post").contains(input.diaryMeal()==null?"":input.diaryMeal()))throw error(HttpStatus.BAD_REQUEST,"Kies een geldige maaltijd.");
        var day=plan.content().days().stream().filter(d->d.day()==input.day()).findFirst().orElseThrow(()->error(HttpStatus.NOT_FOUND,"Dag niet gevonden."));if(input.meal()>=day.meals().size())throw error(HttpStatus.NOT_FOUND,"Maaltijd niet gevonden.");
        LocalDate date=plan.weekStart().plusDays(input.day());if(date.isAfter(LocalDate.now(ZoneId.of("Europe/Brussels"))))throw error(HttpStatus.BAD_REQUEST,"Je kunt een geplande maaltijd pas op die dag registreren.");
        UUID entryId=diaryId(athlete.id(),plan,input.day(),input.meal());jdbc.query("SELECT id FROM cap_user WHERE id=? FOR UPDATE",(r,i)->r.getObject(1),athlete.id());
        var previous=jdbc.query("SELECT payload FROM food_diary_entry WHERE id=? AND athlete_id=?",(r,i)->json.readValue(r.getString(1),FoodDiaryController.Entry.class),entryId,athlete.id());if(!previous.isEmpty())return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(previous.getFirst());
        var meal=day.meals().get(input.meal());var ingredients=new ArrayList<FoodDiaryController.Ingredient>();double calories=0,protein=0,carbs=0,fat=0;
        for(var food:meal.foods()){double factor=food.amount()/(food.unit().equals("stuk")?1:100);double kcal=food.calories()*factor,proteinPart=food.proteinG()*factor,c=food.carbsG()*factor,f=food.fatG()*factor;calories+=kcal;protein+=proteinPart;carbs+=c;fat+=f;ingredients.add(new FoodDiaryController.Ingredient(food.name(),null,food.amount(),food.unit(),kcal,proteinPart,c,f));}
        return diary.add(p,new FoodDiaryController.Entry(entryId,date,input.diaryMeal(),meal.name(),"meal-plan:"+id,1.0,"maaltijd",calories,protein,carbs,fat,List.copyOf(ingredients)));
    }
    @PostMapping public Plan saveJson(Principal p,@RequestBody tools.jackson.databind.JsonNode body){
        // Primitive numbers otherwise turn JSON null into zero, which would mislabel missing nutrition as a verified value.
        var days=body==null?null:body.path("content").path("days");if(days==null||!days.isArray())throw error(HttpStatus.BAD_REQUEST,"Controleer het weekplan.");
        for(var day:days){var list=day.path("meals");if(!list.isArray())throw error(HttpStatus.BAD_REQUEST,"Controleer de maaltijden.");for(var meal:list){var foods=meal.path("foods");if(!foods.isArray())throw error(HttpStatus.BAD_REQUEST,"Controleer de voedingsmiddelen.");for(var food:foods)for(String field:List.of("amount","calories","proteinG","carbsG","fatG"))if(!food.path(field).isNumber())throw error(HttpStatus.BAD_REQUEST,"Vul de portie en alle voedingswaarden in; ontbrekende waarden zijn geen nul.");}}
        Input input;try{input=json.treeToValue(body,Input.class);}catch(Exception e){throw error(HttpStatus.BAD_REQUEST,"Controleer het weekplan.");}return save(p,input);
    }
    public Plan save(Principal p,Input input){
        var actor=user(p);coach(actor);if(input==null)throw error(HttpStatus.BAD_REQUEST,"Plan ontbreekt.");
        Content content=valid(input.content());
        if(input.athleteId()!=null){policy.requireClient(actor,input.athleteId().toString());week(input.weekStart());if(jdbc.queryForObject("SELECT COUNT(*) FROM cap_user WHERE id=? AND role='ATHLETE' AND active=TRUE",Integer.class,input.athleteId())!=1)throw error(HttpStatus.NOT_FOUND,"Klant niet gevonden.");}
        else if(input.weekStart()!=null)throw error(HttpStatus.BAD_REQUEST,"Een sjabloon heeft geen weekdatum.");
        UUID id=input.id()==null?UUID.randomUUID():input.id();String payload=json.writeValueAsString(content);
        if(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>80000)throw error(HttpStatus.BAD_REQUEST,"Dit plan is te groot.");
        if(input.id()==null){
            if(input.version()!=0)throw error(HttpStatus.CONFLICT,"Laad het plan opnieuw.");
            int inserted=jdbc.update("INSERT INTO meal_plan(id,athlete_id,week_start,payload,updated_by) VALUES(?,?,?,?,?) ON CONFLICT DO NOTHING",id,input.athleteId(),input.weekStart(),payload,actor.id());
            if(inserted==0)throw error(HttpStatus.CONFLICT,"Er bestaat al een plan voor deze week. Laad het opnieuw.");
        }else{
            var existing=jdbc.query("SELECT * FROM meal_plan WHERE id=? FOR UPDATE",(r,i)->row(r,false),id);if(existing.isEmpty())throw error(HttpStatus.NOT_FOUND,"Plan niet gevonden.");var old=existing.getFirst();
            if(!Objects.equals(old.athleteId(),input.athleteId())||!Objects.equals(old.weekStart(),input.weekStart()))throw error(HttpStatus.BAD_REQUEST,"Kopieer het plan om een andere klant of week te kiezen.");
            if(jdbc.update("UPDATE meal_plan SET payload=?,version=version+1,updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND version=?",payload,actor.id(),id,input.version())!=1)throw error(HttpStatus.CONFLICT,"Dit plan is gewijzigd. Laad het opnieuw.");
        }
        policy.audit(actor,"MEAL_PLAN_SAVE",id.toString(),"Draft saved");return jdbc.query("SELECT * FROM meal_plan WHERE id=?",(r,i)->row(r,false),id).getFirst();
    }
    @PostMapping("/{id}/publish") public Plan publish(Principal p,@PathVariable UUID id,@RequestBody Version input){
        var actor=user(p);coach(actor);
        var plans=jdbc.query("SELECT * FROM meal_plan WHERE id=? FOR UPDATE",(r,i)->row(r,false),id);if(plans.isEmpty())throw error(HttpStatus.NOT_FOUND,"Plan niet gevonden.");var plan=plans.getFirst();
        if(plan.athleteId()==null||plan.content().days().stream().allMatch(d->d.meals().isEmpty()))throw error(HttpStatus.BAD_REQUEST,"Kies een klant en voeg minstens één maaltijd toe.");
        if(input==null||input.version()!=plan.version())throw error(HttpStatus.CONFLICT,"Laad het actuele concept opnieuw.");
        jdbc.update("UPDATE meal_plan SET published_payload=payload,published_at=CURRENT_TIMESTAMP,version=version+1 WHERE id=?",id);
        policy.audit(actor,"MEAL_PLAN_PUBLISH",id.toString(),"Client copy published");return jdbc.query("SELECT * FROM meal_plan WHERE id=?",(r,i)->row(r,false),id).getFirst();
    }
    @DeleteMapping("/{id}/publication") public void withdraw(Principal p,@PathVariable UUID id,@RequestParam int version){var actor=user(p);coach(actor);if(jdbc.update("UPDATE meal_plan SET published_payload=NULL,published_at=NULL,version=version+1 WHERE id=? AND version=? AND athlete_id IS NOT NULL",id,version)!=1)throw error(HttpStatus.CONFLICT,"Laad het actuele plan opnieuw.");policy.audit(actor,"MEAL_PLAN_WITHDRAW",id.toString(),"Publication withdrawn");}
}
