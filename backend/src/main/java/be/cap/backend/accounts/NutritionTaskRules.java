package be.cap.backend.accounts;

import java.time.*;
import java.util.*;
import java.util.regex.*;

/** Deterministic task updates. Missing fields never erase previous choices. */
public final class NutritionTaskRules {
    private NutritionTaskRules(){}
    public record Targets(double calories,double proteinG,double carbsG,double fatG,String source){}
    public static class State {
        public UUID clientId; public String clientName; public LocalDate weekStart;
        public Double caloriesOverride,proteinOverride,carbsOverride,fatOverride;
        public boolean chooseRemaining; public boolean varied=true; public int variationRevision;public boolean requireNewVariety;
        public Targets targets; public String status="draft"; public String lastError;
        public MealPlanController.Content content; public UUID proposalId,planId; public int planVersion;
        public Integer consentVersion; public List<Map<String,String>> candidates=List.of();
    }
    private static double number(String s){String n=s.replace(" ","");if(n.matches("\\d{1,3}[.,]\\d{3}"))n=n.replace(".","").replace(",","");return Double.parseDouble(n.replace(',','.'));}
    private static Double value(String q,String units){var a=Pattern.compile("(?iu)(\\d+(?:[.,]\\d+)?)\\s*(?:g(?:ram)?\\s*)?(?:"+units+")\\b").matcher(q);if(a.find())return number(a.group(1));var b=Pattern.compile("(?iu)(?:"+units+")\\s*[:=]?\\s*(\\d+(?:[.,]\\d+)?)").matcher(q);return b.find()?number(b.group(1)):null;}
    public static void merge(State s,String message,Clock clock){
        String q=message.toLowerCase(Locale.ROOT);s.requireNewVariety=false;Double v=value(q,"kcal|calorieën|calorieen|calories");if(v!=null)s.caloriesOverride=v;
        v=value(q,"eiwit(?:ten)?|protein(?:e|en)?");if(v!=null)s.proteinOverride=v;
        v=value(q,"koolhydraten|carbs");if(v!=null)s.carbsOverride=v;
        v=value(q,"vet(?:ten)?|fat");if(v!=null)s.fatOverride=v;
        if(q.matches("(?s).*(de rest|overige macro|resterende macro|rest.*kies|rest.*kiezen).*"))s.chooseRemaining=true;
        if(q.matches("(?s).*(variatie|gevarieerd|varied|afwissel).*")){s.varied=true;if(s.content!=null){s.variationRevision++;s.requireNewVariety=true;}}
        if(q.matches("(?s).*(zelfde.*elke dag|iedere dag hetzelfde).*"))s.varied=false;
        LocalDate today=LocalDate.now(clock.withZone(ZoneId.of("Europe/Brussels"))),explicit=null;
        var iso=Pattern.compile("\\b(20\\d{2}-\\d{2}-\\d{2})\\b").matcher(q);if(iso.find()){explicit=LocalDate.parse(iso.group(1));if(iso.find()&&!LocalDate.parse(iso.group(1)).equals(explicit.plusDays(6)))throw new IllegalArgumentException("Kies een volledige week van maandag tot zondag. De andere gegevens zijn behouden.");}
        if(explicit==null){String[] months={"januari","februari","maart","april","mei","juni","juli","augustus","september","oktober","november","december"};var date=Pattern.compile("\\b(\\d{1,2})(?:\\s*[-–—]\\s*(\\d{1,2}))?\\s+("+String.join("|",months)+")(?:\\s+(20\\d{2}))?\\b").matcher(q);if(date.find()){int month=Arrays.asList(months).indexOf(date.group(3))+1,year=date.group(4)==null?today.getYear():Integer.parseInt(date.group(4));explicit=LocalDate.of(year,month,Integer.parseInt(date.group(1)));if(date.group(2)!=null&&Integer.parseInt(date.group(2))!=explicit.plusDays(6).getDayOfMonth())throw new IllegalArgumentException("CAP bewaart weekplannen van maandag tot zondag. Kies een volledige week; de andere gegevens zijn behouden.");}}
        if(explicit!=null){if(explicit.getDayOfWeek()!=DayOfWeek.MONDAY)throw new IllegalArgumentException("CAP bewaart weekplannen van maandag tot zondag. Welke maandag wil je als start? De andere gegevens zijn behouden.");s.weekStart=explicit;}
        else if(q.contains("volgende week")||q.contains("komende week"))s.weekStart=today.with(DayOfWeek.MONDAY).plusWeeks(1);
        else if(q.contains("deze week"))s.weekStart=today.with(DayOfWeek.MONDAY);
        if(s.weekStart==null)s.weekStart=today.with(DayOfWeek.MONDAY).plusWeeks(1);
    }
    public static Targets targets(State s,Map<String,Object> saved){
        Double e=s.caloriesOverride!=null?s.caloriesOverride:n(saved,"calories");if(e==null||e<1)throw new IllegalArgumentException("Er is geen opgeslagen caloriedoel. Hoeveel kcal per dag wil je voor dit concept gebruiken?");
        Double p=s.proteinOverride!=null?s.proteinOverride:n(saved,"proteinG");if(p==null)p=e*.20/4;
        Double c=s.carbsOverride!=null?s.carbsOverride:s.chooseRemaining?null:n(saved,"carbsG"),f=s.fatOverride!=null?s.fatOverride:s.chooseRemaining?null:n(saved,"fatG");
        if(s.caloriesOverride!=null||s.proteinOverride!=null){if(s.carbsOverride==null)c=null;if(s.fatOverride==null)f=null;}
        if(f==null&&c==null){f=e*.30/9;c=(e-4*p-9*f)/4;}else if(f==null)f=(e-4*p-4*c)/9;else if(c==null)c=(e-4*p-9*f)/4;
        if(e<800||e>8000||p<0||p>400||c<0||f<0||Math.abs(4*p+4*c+9*f-e)>e*.05)throw new IllegalArgumentException("De doelen passen niet bij elkaar. Welk calorie- of macrodoel wil je aanpassen?");
        String source=s.caloriesOverride!=null||s.proteinOverride!=null||s.carbsOverride!=null||s.fatOverride!=null?"expliciete doelen voor dit concept; opgeslagen doelen blijven ongewijzigd":saved.isEmpty()?"afgeleid voor dit concept":"opgeslagen doelen; ontbrekende macro’s afgeleid";
        return new Targets(e,p,c,f,source);
    }
    private static Double n(Map<String,Object> map,String key){var v=map.get(key);return v instanceof Number num&&Double.isFinite(num.doubleValue())?num.doubleValue():null;}
}
