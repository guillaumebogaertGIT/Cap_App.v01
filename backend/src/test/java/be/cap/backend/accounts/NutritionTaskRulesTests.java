package be.cap.backend.accounts;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class NutritionTaskRulesTests {
    Clock clock(int day){return Clock.fixed(Instant.parse("2026-11-"+day+"T12:00:00Z"),ZoneOffset.UTC);}
    @Test void deterministicDatesAndFollowupKeepExplicitWeekAndTargets(){for(int day:List.of(10,11)){var s=new NutritionTaskRules.State();NutritionTaskRules.merge(s,"volgende week 3000 kcal en 180 g eiwit, kies de rest",clock(day));assertEquals(LocalDate.of(2026,11,16),s.weekStart);s.targets=NutritionTaskRules.targets(s,Map.of());assertEquals(180,s.targets.proteinG());assertEquals(345,s.targets.carbsG());assertEquals(100,s.targets.fatG());NutritionTaskRules.merge(s,"Ja, meer variatie",clock(day));assertEquals(LocalDate.of(2026,11,16),s.weekStart);assertEquals(3000,s.caloriesOverride);NutritionTaskRules.merge(s,"deze week",clock(day));assertEquals(LocalDate.of(2026,11,9),s.weekStart);NutritionTaskRules.merge(s,"16–22 november 2026",clock(day));assertEquals(LocalDate.of(2026,11,16),s.weekStart);}}
    @Test void partialTargetsAreDerivedWithoutChangingSavedData(){var s=new NutritionTaskRules.State();var saved=Map.<String,Object>of("calories",2400,"proteinG",160);var t=NutritionTaskRules.targets(s,saved);assertEquals(2400,t.calories());assertEquals(160,t.proteinG());assertEquals(2400,4*t.proteinG()+4*t.carbsG()+9*t.fatG(),.001);NutritionTaskRules.merge(s,"2800 kcal, 180g eiwit",clock(10));assertEquals(2800,NutritionTaskRules.targets(s,saved).calories());assertEquals(2400,saved.get("calories"));}
}
