package be.cap.backend.accounts;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Builds bounded, reviewable edits; it never writes a meal plan. */
@Service @Profile("accounts")
public class AiNutritionPlanner {
    public record Input(UUID id,UUID athleteId,String instruction,JsonNode draft,String scope,Integer day,Integer meal,boolean shareProfile,String allergies,boolean shareAllergies){}
    private final JdbcTemplate jdbc;private final StaffPolicy policy;private final MealPlanController meals;
    private final JsonMapper json=JsonMapper.builder().build();
    public AiNutritionPlanner(JdbcTemplate jdbc,StaffPolicy policy,MealPlanController meals){this.jdbc=jdbc;this.policy=policy;this.meals=meals;}
    private ResponseStatusException invalid(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
    public Map<String,Object> context(AccountService.Account actor,Input input){
        policy.require(actor,StaffPolicy.Permission.NUTRITION_MANAGE);
        if(input==null||input.id()==null||input.athleteId()==null||input.instruction()==null||input.instruction().isBlank()||input.instruction().length()>2000)throw invalid("Kies een klant en geef een opdracht van maximaal 2000 tekens.");
        policy.requireClient(actor,input.athleteId().toString());
        if(jdbc.queryForObject("SELECT COUNT(*) FROM cap_user WHERE id=? AND role='ATHLETE' AND active=TRUE",Integer.class,input.athleteId())!=1)throw invalid("Deze klant is niet beschikbaar.");
        if(!Set.of("edit","week","day","meal").contains(input.scope()==null?"":input.scope()))throw invalid("Kies welk deel van het plan je wilt aanpassen.");
        if(input.draft()==null||!input.draft().isObject()||!input.draft().path("days").isArray()||input.draft().path("days").size()!=7||json.writeValueAsBytes(input.draft()).length>18000)throw invalid("Het concept moet zeven dagen bevatten en mag niet te groot zijn.");
        if((input.scope().equals("day")||input.scope().equals("meal"))&&(input.day()==null||input.day()<0||input.day()>6))throw invalid("Kies een dag.");
        if(input.scope().equals("meal")&&(input.meal()==null||input.meal()<0))throw invalid("Kies een maaltijd.");
        if(input.allergies()!=null&&input.allergies().length()>500)throw invalid("Gebruik maximaal 500 tekens voor allergieën.");
        if(input.allergies()!=null&&!input.allergies().isBlank()&&!input.shareAllergies())throw invalid("Bevestig dat deze allergie-informatie voor deze aanvraag met OpenAI wordt gedeeld.");
        String supplied=input.instruction()+" "+json.writeValueAsString(input.draft());
        if(!input.shareAllergies()&&java.util.regex.Pattern.compile("(?iu)allerg|intoleran|coeliak|coeliac|anafyl|anaphyl").matcher(supplied).find())throw invalid("De opdracht of het concept noemt een allergie of intolerantie. Bevestig delen voor deze aanvraag of verwijder die informatie.");
        var result=new LinkedHashMap<String,Object>();result.put("draft",input.draft());result.put("scope",input.scope());result.put("day",input.day());result.put("meal",input.meal());result.put("foods",meals.foods());
        result.put("allergies",input.shareAllergies()&&input.allergies()!=null&&!input.allergies().isBlank()?input.allergies().trim():"Onbekend; vraag hiernaar en doe geen aannames.");
        if(input.shareProfile())result.putAll(profile(actor,input.athleteId()));
        return result;
    }
    public Map<String,Object> profile(AccountService.Account actor,UUID athleteId){
        policy.require(actor,StaffPolicy.Permission.NUTRITION_MANAGE);policy.require(actor,StaffPolicy.Permission.CLIENT_HEALTH);
        if(athleteId==null)throw invalid("Kies een klant.");policy.requireClient(actor,athleteId.toString());
        if(jdbc.queryForObject("SELECT COUNT(*) FROM cap_user WHERE id=? AND role='ATHLETE' AND active=TRUE",Integer.class,athleteId)!=1)throw invalid("Deze klant is niet beschikbaar.");
        var result=new LinkedHashMap<String,Object>();var profile=jdbc.query("SELECT values_json FROM account_profile WHERE user_id=?",(r,i)->r.getString(1),athleteId);
        if(!profile.isEmpty()){var data=json.readTree(profile.getFirst());result.put("goal",data.path("goal").asText());result.put("experience",data.path("experience").asText());}
        var target=jdbc.queryForList("SELECT calories,protein_g,carbs_g,fat_g,water_ml FROM nutrition_target WHERE athlete_id=?",athleteId);if(!target.isEmpty())result.put("targets",target.getFirst());
        var today=LocalDate.now(ZoneId.of("Europe/Brussels"));result.put("training",jdbc.queryForList("SELECT s.title,s.session_date,s.start_time FROM club_session s JOIN session_booking b ON b.session_id=s.id WHERE b.athlete_id=? AND b.cancelled=FALSE AND s.session_date BETWEEN ? AND ? ORDER BY s.session_date,s.start_time LIMIT 8",athleteId,today,today.plusDays(14)));
        return result;
    }
    public Map<String,Object> apply(Input input,String response){
        JsonNode root;try{root=json.readTree(response);}catch(Exception e){throw invalid("Het AI-antwoord kon niet worden gelezen.");}
        String answer=root.path("answer").asText();JsonNode patches=root.path("patches");
        if(answer.isBlank()||answer.length()>4000||!patches.isArray()||patches.size()>42)throw invalid("Het AI-voorstel is onvolledig.");
        ObjectNode result=(ObjectNode)input.draft().deepCopy();ArrayNode days=(ArrayNode)result.path("days");
        var seenDays=new HashSet<Integer>();for(JsonNode day:days)if(!day.isObject()||!day.path("day").isInt()||day.path("day").asInt()<0||day.path("day").asInt()>6||!seenDays.add(day.path("day").asInt())||!day.path("meals").isArray())throw invalid("De dagen in het concept zijn ongeldig.");
        var seen=new HashSet<String>();for(JsonNode patch:patches){
            if(!patch.path("day").isInt()||!patch.path("mealIndex").isInt()||!patch.path("meals").isArray())throw invalid("Een voorgestelde wijziging is onvolledig.");
            int day=patch.path("day").asInt(),index=patch.path("mealIndex").asInt();if(day<0||day>6||index< -1||!seen.add(day+":"+index))throw invalid("Een voorgestelde wijziging is ongeldig.");
            if((input.scope().equals("day")||input.scope().equals("meal"))&&!Objects.equals(input.day(),day)||input.scope().equals("meal")&&index!=input.meal())throw invalid("Het voorstel wijzigt een ander deel van het plan.");
            ObjectNode target=null;for(JsonNode candidate:days)if(candidate.path("day").asInt()==day)target=(ObjectNode)candidate;
            ArrayNode existing=(ArrayNode)Objects.requireNonNull(target).path("meals");
            if(index==-1&&input.scope().equals("edit")&&!existing.isEmpty())throw invalid("Kies een dag of de hele week om bestaande maaltijden te vervangen.");
            if(index>=0&&(index>=existing.size()||patch.path("meals").size()!=1))throw invalid("De te vervangen maaltijd bestaat niet meer.");
            var converted=json.createArrayNode();for(JsonNode meal:patch.path("meals"))converted.add(convertMeal(meal));
            if(converted.size()>6)throw invalid("Maximaal zes maaltijden per dag.");
            if(index==-1)target.set("meals",converted);else existing.set(index,converted.get(0));
        }
        return Map.of("answer",answer,"content",result,"changed",!patches.isEmpty());
    }
    private ObjectNode convertMeal(JsonNode meal){
        String name=meal.path("name").asText().trim();var input=meal.path("foods");if(name.isBlank()||name.length()>60||!input.isArray()||input.isEmpty()||input.size()>12)throw invalid("Controleer de voorgestelde maaltijd en ingrediënten.");
        ArrayNode result=json.createArrayNode();var references=meals.foods();for(JsonNode food:input){
            String foodName=food.path("name").asText();var found=references.stream().filter(f->f.name().equalsIgnoreCase(foodName)).toList();if(found.size()!=1)throw invalid("Een voorgesteld voedingsmiddel is niet eenduidig in de CAP-referentielijst gevonden.");
            if(!food.path("amount").isNumber())throw invalid("Een voorgestelde hoeveelheid ontbreekt.");double amount=food.path("amount").asDouble();if(!Double.isFinite(amount)||amount<=0||amount>5000)throw invalid("Controleer de voorgestelde hoeveelheden.");
            var reference=found.getFirst();result.add(json.valueToTree(new MealPlanController.Food(reference.name(),reference.preparation(),reference.unit(),amount,reference.calories(),reference.proteinG(),reference.carbsG(),reference.fatG())));
        }
        String instructions=meal.path("instructions").asText("").trim();if(instructions.length()>1000)throw invalid("De bereidingsinstructies zijn te lang.");ObjectNode converted=json.createObjectNode();converted.put("name",name);converted.put("instructions",instructions);converted.set("foods",result);return converted;
    }
}
