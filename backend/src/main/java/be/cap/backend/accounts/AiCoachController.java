package be.cap.backend.accounts;

import java.security.Principal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController @Profile("accounts") @RequestMapping("/api/ai-coach")
public class AiCoachController {
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(AiCoachController.class);
    public record Input(UUID id,String message,List<AiCoachGateway.Turn> history,boolean consent,String mode,Boolean ownWorkouts,Boolean ownNutrition,Boolean ownProfile,Boolean ownBookings,Boolean ownMealPlan) {}
    private final AiOwnContext ownContext;
    private final AiCoachSettings settings;private final AiCoachActions actions;private final AiNutritionPlanner nutritionPlanner;private final NutritionPreferences preferences;private final MeasurementService measurements;
    private final StaffPolicy policy;private final AiWorkoutDrafts drafts;
    private final JdbcTemplate jdbc;private final AccountService accounts;private final AiCoachGateway gateway;private final TransactionTemplate transaction;
    private final boolean enabled;private final Set<String> testers;
    public AiCoachController(JdbcTemplate jdbc,AccountService accounts,AiCoachGateway gateway,PlatformTransactionManager manager,StaffPolicy policy,AiWorkoutDrafts drafts,AiOwnContext ownContext,AiCoachSettings settings,AiCoachActions actions,AiNutritionPlanner nutritionPlanner,NutritionPreferences preferences,MeasurementService measurements,
        @Value("${cap.ai-coach.enabled:false}") boolean enabled,@Value("${cap.ai-coach.testers:}") String testers){
        this.jdbc=jdbc;this.accounts=accounts;this.gateway=gateway;this.policy=policy;this.drafts=drafts;this.ownContext=ownContext;this.settings=settings;this.actions=actions;this.nutritionPlanner=nutritionPlanner;this.preferences=preferences;this.measurements=measurements;transaction=new TransactionTemplate(manager);this.enabled=enabled;
        this.testers=new HashSet<>();for(String email:testers.split(","))if(!email.isBlank())this.testers.add(email.trim().toLowerCase(Locale.ROOT));
    }
    private AccountService.Account user(Principal p){if(p==null)throw error(HttpStatus.UNAUTHORIZED,"Log opnieuw in.");return accounts.account(p.getName());}
    private boolean allowed(AccountService.Account user){return Set.of("ATHLETE","COACH").contains(user.role())&&testers.contains(user.email().toLowerCase(Locale.ROOT));}
    private boolean ready(AccountService.Account user){return allowed(user)&&enabled&&gateway.configured();}
    private ResponseStatusException error(HttpStatus status,String message){return new ResponseStatusException(status,message);}
    @GetMapping public ResponseEntity<?> status(Principal p){var user=user(p);return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("available",ready(user),"canDraft",ready(user)&&policy.allows(user,StaffPolicy.Permission.WORKOUT_TEMPLATE_MANAGE),"testUser",allowed(user),"settings",settings.read(user),"clientDataApproved",user.role().equals("ATHLETE")&&preferences.sharedWithAi(user.id()).isPresent(),"remainingRequests",ready(user)?500-jdbc.queryForObject("SELECT reserved_cents FROM ai_coach_budget WHERE id=1",Integer.class):0));}
    @PostMapping("/actions/{id}/confirm") public Map<String,Object> confirm(Principal p,@PathVariable UUID id) throws Exception {var actor=user(p);if(!actor.role().equals("COACH")||!ready(actor))throw error(HttpStatus.FORBIDDEN,"Alleen bevoegde coaches kunnen acties bevestigen.");return actions.confirm(actor,id);}
    @DeleteMapping("/actions/{id}") public void cancel(Principal p,@PathVariable UUID id){var actor=user(p);jdbc.update("DELETE FROM ai_coach_action WHERE id=? AND user_id=? AND result IS NULL",id,actor.id());}
    private void reserve(AccountService.Account user,UUID id){
        transaction.executeWithoutResult(tx->{
            int used=jdbc.queryForObject("SELECT reserved_cents FROM ai_coach_budget WHERE id=1 FOR UPDATE",Integer.class);
            if(jdbc.queryForObject("SELECT COUNT(*) FROM ai_coach_request WHERE id=?",Integer.class,id)>0)throw error(HttpStatus.CONFLICT,"Deze vraag is al verwerkt. Verstuur een nieuwe vraag.");
            if(used>=500)throw error(HttpStatus.TOO_MANY_REQUESTS,"Het gedeelde AI-testbudget is op. Er worden geen nieuwe AI-verzoeken verstuurd.");
            jdbc.update("UPDATE ai_coach_budget SET reserved_cents=reserved_cents+1 WHERE id=1");
            jdbc.update("INSERT INTO ai_coach_request(id,user_id,state) VALUES(?,?,'reserved')",id,user.id());
        });
    }
    @GetMapping("/nutrition-profile") public ResponseEntity<?> nutritionProfile(Principal p,@RequestParam UUID athleteId){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(nutritionPlanner.profile(user(p),athleteId));}
    @PostMapping("/diagnostic") public ResponseEntity<?> diagnostic(Principal p,@RequestBody VersionedDiagnostic input){
        var actor=user(p);if(!policy.role(actor).equals("OWNER")||!ready(actor))throw error(HttpStatus.FORBIDDEN,"Deze verbindingstest is alleen beschikbaar voor een toegestane beheerder.");
        if(input==null||input.id()==null)throw error(HttpStatus.BAD_REQUEST,"Testreferentie ontbreekt.");
        if(!settings.read(actor).values().consent())throw error(HttpStatus.FORBIDDEN,"Schakel Coach Glenn eerst in bij de instellingen.");
        reserve(actor,input.id());try{String result=gateway.diagnostic();jdbc.update("UPDATE ai_coach_request SET state='completed' WHERE id=?",input.id());return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("result",result));}
        catch(Exception failure){if(failure instanceof InterruptedException)Thread.currentThread().interrupt();jdbc.update("UPDATE ai_coach_request SET state='failed' WHERE id=?",input.id());String result=failure instanceof IllegalStateException&&failure.getMessage()!=null?failure.getMessage():"De verbindingstest is mislukt zonder aanvullende informatie.";return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("result",result));}
    }
    public record VersionedDiagnostic(UUID id){}
    @PostMapping("/nutrition-draft") public ResponseEntity<?> nutritionDraft(Principal p,@RequestBody AiNutritionPlanner.Input input){
        var user=user(p);if(!ready(user))throw error(HttpStatus.FORBIDDEN,"Coach Glenn is niet beschikbaar voor dit account.");
        var prefs=settings.read(user).values();if(!prefs.consent()||!prefs.coachActions())throw error(HttpStatus.FORBIDDEN,"Schakel Coach Glenn en coachacties in via de instellingen.");
        var context=nutritionPlanner.context(user,input,true);reserve(user,input.id());
        try{var result=nutritionPlanner.apply(input,gateway.nutritionDraft(input.instruction().trim(),context));jdbc.update("UPDATE ai_coach_request SET state='completed' WHERE id=?",input.id());return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);}
        catch(Exception failure){if(failure instanceof InterruptedException)Thread.currentThread().interrupt();jdbc.update("UPDATE ai_coach_request SET state='failed' WHERE id=?",input.id());
            if(failure instanceof ResponseStatusException invalid)throw error(HttpStatus.BAD_REQUEST,invalid.getReason()+" Je concept is niet gewijzigd.");
            String diagnostic=failure instanceof AiCoachFailure known?known.diagnostic():"provider_connection";LOG.warn("AI nutrition request {} failed: {}",input.id(),diagnostic);
            throw error(HttpStatus.SERVICE_UNAVAILABLE,"Het AI-voorstel kon niet worden gemaakt. Je concept is niet gewijzigd; de budgetreservering blijft behouden. Referentie: "+input.id()+" ("+diagnostic+").");}
    }
    @PostMapping public ResponseEntity<?> answer(Principal p,@RequestBody Input input){
        var user=user(p);if(!allowed(user))throw error(HttpStatus.FORBIDDEN,"Deze AI-test is nog niet beschikbaar voor jouw account.");
        if(!ready(user))throw error(HttpStatus.SERVICE_UNAVAILABLE,"De AI-test is nog niet ingeschakeld.");
        if(input==null||input.id()==null||!input.consent()||input.message()==null||input.message().isBlank()||input.message().length()>4000)throw error(HttpStatus.BAD_REQUEST,"Bevestig de AI-melding en stel een vraag van maximaal 4000 tekens.");
        var savedSettings=settings.read(user);var prefs=savedSettings.values();if(savedSettings.version()>0&&!prefs.consent())throw error(HttpStatus.FORBIDDEN,"Schakel Coach Glenn in via de instellingen op de Coach Glenn-pagina.");
        boolean actionMode=savedSettings.version()>0&&prefs.coachActions()&&user.role().equals("COACH");
        var turns=new ArrayList<AiCoachGateway.Turn>();
        if(input.history()!=null){if(input.history().size()>6)throw error(HttpStatus.BAD_REQUEST,"Begin een nieuw gesprek.");for(var turn:input.history()){
            if(turn==null||!Set.of("user","assistant").contains(turn.role()==null?"":turn.role())||turn.content()==null||turn.content().isBlank()||turn.content().length()>8000)throw error(HttpStatus.BAD_REQUEST,"Ongeldig gesprek.");turns.add(turn);
        }}
        turns.add(new AiCoachGateway.Turn("user",input.message().trim()));
        boolean workout="workout".equals(input.mode())||("auto".equals(input.mode())&&policy.allows(user,StaffPolicy.Permission.WORKOUT_TEMPLATE_MANAGE));
        if(input.mode()!=null&&!Set.of("chat","workout","auto").contains(input.mode()))throw error(HttpStatus.BAD_REQUEST,"Onbekende AI-modus.");
        if(workout)policy.require(user,StaffPolicy.Permission.WORKOUT_TEMPLATE_MANAGE);
        List<String> candidates=List.of();
        if(workout||actionMode&&policy.allows(user,StaffPolicy.Permission.WORKOUT_VIEW)){try{candidates=drafts.candidates(user,input.message());}catch(Exception e){throw error(HttpStatus.SERVICE_UNAVAILABLE,"De oefenbibliotheek kon niet laden.");}}
        if(turns.stream().mapToInt(t->t.content().getBytes(StandardCharsets.UTF_8).length).sum()>8000)throw error(HttpStatus.BAD_REQUEST,"Dit gesprek is te lang. Kies Gesprek wissen.");
        String personalContext;
        try{
            var approval=user.role().equals("ATHLETE")?preferences.sharedWithAi(user.id()):Optional.<NutritionPreferences.Values>empty();
            if(user.role().equals("ATHLETE")){
                if(approval.isEmpty())personalContext="";
                else {String q=input.message().toLowerCase(Locale.ROOT);boolean food=q.matches("(?s).*(voed|maaltijd|eet|calorie|eiwit|macro|allerg|intoler|vermijd|meal|nutrition|diet).*"),training=q.matches("(?s).*(train|workout|oefen|kracht|record|squat|bench|deadlift).*"),booking=q.matches("(?s).*(boek|sessie|planning|afspraak|les|agenda).*"),measure=q.matches("(?s).*(gewicht|lengte|lichaam|meting|vetpercentage|progress|voortgang|profiel).*");boolean general=!food&&!training&&!booking&&!measure;
                    String snapshot=ownContext.read(user,training||general,food||general,true,booking||general,food||general);var mapper=tools.jackson.databind.json.JsonMapper.builder().build();var data=(tools.jackson.databind.node.ObjectNode)mapper.readTree(snapshot);var values=approval.get();
                    if(food||general)data.set("dietaryInformation",mapper.valueToTree(Map.of("allergies",values.allergies(),"avoidedFoods",values.avoidedFoods())));
                    if(measure||general){var m=measurements.snapshot(user.id());data.set("measurements",mapper.valueToTree(Map.of("profile",m.profile(),"recent",m.measurements().stream().limit(5).map(entry->Map.of("recordedAt",Objects.toString(entry.recordedAt(),""),"values",entry.values())).toList())));}
                    personalContext=mapper.writeValueAsString(data);
                }
            }else personalContext=ownContext.read(user,savedSettings.version()>0?prefs.ownWorkouts():Boolean.TRUE.equals(input.ownWorkouts()),savedSettings.version()>0?prefs.ownNutrition():Boolean.TRUE.equals(input.ownNutrition()),savedSettings.version()>0?prefs.ownProfile():Boolean.TRUE.equals(input.ownProfile()),savedSettings.version()>0?prefs.ownBookings():Boolean.TRUE.equals(input.ownBookings()),savedSettings.version()>0?prefs.ownMealPlan():Boolean.TRUE.equals(input.ownMealPlan()));
        }catch(Exception unavailable){throw error(HttpStatus.SERVICE_UNAVAILABLE,"Je eigen gegevens konden niet veilig laden. Er is geen AI-verzoek verstuurd.");}
        Map<String,Object> resources;try{resources=actionMode?actions.resourcesForChat(user,turns.stream().map(AiCoachGateway.Turn::content).collect(java.util.stream.Collectors.joining("\n")),input.message(),candidates,true):Map.of();}catch(Exception e){throw error(HttpStatus.SERVICE_UNAVAILABLE,"Coachgegevens konden niet laden. Er is geen AI-verzoek verstuurd.");}
        reserve(user,input.id());
        try{
            Map<String,Object> result;
            if(actionMode){String output=gateway.propose(List.copyOf(turns),resources,personalContext);result=actions.stage(user,input.id(),output,resources);}
            else if(workout){String output=personalContext.isEmpty()?gateway.draft(List.copyOf(turns),candidates):gateway.draft(List.copyOf(turns),candidates,personalContext);try{result=drafts.validate(output,candidates);}catch(Exception invalid){throw new AiCoachFailure("draft_invalid");}}
            else result=Map.of("answer",personalContext.isEmpty()?gateway.answer(List.copyOf(turns)):gateway.answer(List.copyOf(turns),personalContext));
            jdbc.update("UPDATE ai_coach_request SET state='completed' WHERE id=?",input.id());
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
        }catch(Exception failure){
            if(failure instanceof InterruptedException)Thread.currentThread().interrupt();
            jdbc.update("UPDATE ai_coach_request SET state='failed' WHERE id=?",input.id());
            if(failure instanceof ResponseStatusException invalid)throw error(invalid.getStatusCode().is4xxClientError()?HttpStatus.BAD_REQUEST:HttpStatus.SERVICE_UNAVAILABLE,invalid.getReason()+" Er is niets opgeslagen; vraag een aangepast voorstel.");
            String code=failure instanceof AiCoachFailure known?known.code():failure instanceof java.net.http.HttpTimeoutException?"provider_timeout":"provider_connection";
            String diagnostic=failure instanceof AiCoachFailure known?known.diagnostic():code;
            LOG.warn("AI request {} failed: {}",input.id(),diagnostic);
            String message=failure instanceof AiCoachFailure known?known.userMessage():code.equals("provider_timeout")?"De AI-dienst antwoordde niet op tijd.":"De verbinding met de AI-dienst is mislukt.";
            if(code.equals("draft_invalid"))message="Het AI-workoutconcept kon niet veilig worden verwerkt.";
            throw error(HttpStatus.SERVICE_UNAVAILABLE,message+" Geen automatische herhaling; de budgetreservering blijft behouden. Referentie: "+input.id()+" ("+diagnostic+").");
        }
    }
}
