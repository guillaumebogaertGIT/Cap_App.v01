package be.cap.backend.accounts;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

/** Canonical food values, bounded portion adjustment and per-day validation. No model totals. */
@Service @Profile("accounts")
public class NutritionPlanValidator {
    public record Totals(double calories,double proteinG,double carbsG,double fatG){}
    public record Validated(MealPlanController.Content content,List<Totals> dailyTotals){}
    private final MealPlanController plans;private final double calorieTolerance,proteinTolerance;
    public NutritionPlanValidator(MealPlanController plans,@Value("${cap.ai-nutrition.calorie-tolerance:0.05}") double calorieTolerance,@Value("${cap.ai-nutrition.protein-tolerance-g:10}") double proteinTolerance){this.plans=plans;this.calorieTolerance=calorieTolerance;this.proteinTolerance=proteinTolerance;}
    public double calorieTolerance(){return calorieTolerance;}public double proteinTolerance(){return proteinTolerance;}
    public List<MealPlanController.Food> foods(String restrictions){
        String q=restrictions.toLowerCase(Locale.ROOT);return plans.foods().stream().filter(f->{String name=f.name().toLowerCase(Locale.ROOT);
            if(q.contains(name))return false;
            if((q.contains("pinda")||q.contains("noten"))&&(name.contains("pinda")||name.contains("noten")))return false;
            if((q.contains("melk")||q.contains("lactose")||q.contains("zuivel")||q.contains("vegan"))&&(name.contains("kwark")||name.contains("cheese")||name.contains("feta")))return false;
            if((q.contains("gluten")||q.contains("tarwe")||q.contains("coeliak"))&&(name.contains("brood")||name.contains("pasta")||name.contains("havermout")))return false;
            if((q.contains("vegetari")||q.contains("vegan"))&&(name.contains("vlees")||name.contains("vis")))return false;
            if(q.contains("vis")&&name.contains("vis"))return false;
            if((q.contains("vegan")||q.matches("(?s).*\\bei\\b.*"))&&name.equals("ei"))return false;
            return !q.contains("vegan")||!name.contains("honing");
        }).toList();
    }
    public static Totals totals(List<MealPlanController.Meal> meals){double[] sum=new double[4];for(var meal:meals)for(var food:meal.foods()){double factor=food.amount()/(food.unit().equals("stuk")?1:100);sum[0]+=food.calories()*factor;sum[1]+=food.proteinG()*factor;sum[2]+=food.carbsG()*factor;sum[3]+=food.fatG()*factor;}return new Totals(sum[0],sum[1],sum[2],sum[3]);}
    public boolean meets(Totals t,NutritionTaskRules.Targets goals){return Math.abs(t.calories()-goals.calories())<=goals.calories()*calorieTolerance&&Math.abs(t.proteinG()-goals.proteinG())<=proteinTolerance;}
    public Validated validate(JsonNode input,NutritionTaskRules.State state,List<MealPlanController.Food> references){
        if(input==null||!input.path("days").isArray()||input.path("days").size()!=7)throw new IllegalArgumentException("Het voorstel moet alle zeven dagen volledig bevatten.");
        var days=new ArrayList<MealPlanController.Day>();var seen=new HashSet<Integer>();var byName=new HashMap<String,MealPlanController.Food>();references.forEach(f->byName.put(f.name(),f));
        for(var day:input.path("days")){int index=day.path("day").asInt(-1);if(index<0||index>6||!seen.add(index)||!day.path("meals").isArray()||day.path("meals").size()<3||day.path("meals").size()>6)throw new IllegalArgumentException("Elke dag moet drie tot zes complete maaltijden bevatten.");var meals=new ArrayList<MealPlanController.Meal>();
            for(var meal:day.path("meals")){String name=meal.path("name").asText(),instructions=meal.path("instructions").asText("");if(name.isBlank()||name.length()>60||instructions.length()>1000||!meal.path("foods").isArray()||meal.path("foods").isEmpty()||meal.path("foods").size()>12)throw new IllegalArgumentException("Een maaltijd is onvolledig.");var items=new ArrayList<MealPlanController.Food>();
                for(var item:meal.path("foods")){var ref=byName.get(item.path("name").asText());if(ref==null||!item.path("amount").isNumber())throw new IllegalArgumentException("Gebruik uitsluitend beschikbare voedingsmiddelen met een geldige hoeveelheid.");double amount=item.path("amount").asDouble();if(!Double.isFinite(amount)||amount<=0||amount>1500||ref.unit().equals("stuk")&&(amount!=Math.rint(amount)||amount>8))throw new IllegalArgumentException("Controleer de porties en het aantal stuks.");items.add(withAmount(ref,amount));}
                meals.add(new MealPlanController.Meal(name,List.copyOf(items),instructions));
            }days.add(new MealPlanController.Day(index,balance(meals,state.targets)));
        }
        days.sort(Comparator.comparingInt(MealPlanController.Day::day));String name=input.path("name").asText("Voedingsplan");MealPlanController.Content content;
        try{content=plans.valid(new MealPlanController.Content(name,days));}catch(org.springframework.web.server.ResponseStatusException invalid){throw new IllegalArgumentException(invalid.getReason());}
        for(var day:content.days()){var t=totals(day.meals());if(!meets(t,state.targets))throw new IllegalArgumentException("Dag "+(day.day()+1)+" haalt na portie-aanpassing de doelen niet: "+Math.round(t.calories())+" kcal en "+Math.round(t.proteinG())+" g eiwit. Kies andere voedingsmiddelen.");if(state.carbsOverride!=null&&Math.abs(t.carbsG()-state.targets.carbsG())>Math.max(10,state.targets.carbsG()*.1)||state.fatOverride!=null&&Math.abs(t.fatG()-state.targets.fatG())>Math.max(5,state.targets.fatG()*.1))throw new IllegalArgumentException("De expliciete koolhydraat- of vetdoelen worden niet gehaald.");}
        if(state.varied){long unique=content.days().stream().map(d->signature(d.meals())).distinct().count();if(unique<4)throw new IllegalArgumentException("Het weekplan heeft te weinig echte variatie. Verander maaltijden en ingrediënten op minstens vier dagen.");if(state.requireNewVariety&&state.content!=null){int changed=0;for(int i=0;i<7;i++)if(!signature(content.days().get(i).meals()).equals(signature(state.content.days().get(i).meals())))changed++;if(changed<3)throw new IllegalArgumentException("Meer variatie moet de ingrediënten of maaltijdsamenstelling van minstens drie dagen wijzigen.");}}
        return new Validated(content,content.days().stream().map(d->totals(d.meals())).toList());
    }
    private static String signature(List<MealPlanController.Meal> meals){return meals.stream().map(m->m.foods().stream().map(MealPlanController.Food::name).sorted().toList().toString()).sorted().toList().toString();}
    private static MealPlanController.Food withAmount(MealPlanController.Food f,double amount){return new MealPlanController.Food(f.name(),f.preparation(),f.unit(),amount,f.calories(),f.proteinG(),f.carbsG(),f.fatG());}
    private List<MealPlanController.Meal> balance(List<MealPlanController.Meal> meals,NutritionTaskRules.Targets target){
        var all=meals.stream().flatMap(m->m.foods().stream()).toList();int count=all.size();double[] x=new double[count],original=new double[count],low=new double[count],high=new double[count];double[][] a=new double[count][4];double[] goal={target.calories(),target.proteinG(),target.carbsG(),target.fatG()},scale={Math.max(30,target.calories()*.02),5,Math.max(15,target.carbsG()*.08),Math.max(5,target.fatG()*.08)},sum=new double[4];
        for(int i=0;i<count;i++){var f=all.get(i);x[i]=original[i]=f.amount();low[i]=f.unit().equals("stuk")?1:Math.max(2,f.amount()*.35);high[i]=f.unit().equals("stuk")?Math.min(8,Math.max(f.amount(),f.amount()*2)):Math.min(f.calories()>500?90:f.preparation().equals("droog")?300:650,Math.max(f.amount(),f.amount()*3));low[i]=Math.min(low[i],high[i]);x[i]=Math.max(low[i],Math.min(high[i],x[i]));double divisor=f.unit().equals("stuk")?1:100;a[i]=new double[]{f.calories()/divisor,f.proteinG()/divisor,f.carbsG()/divisor,f.fatG()/divisor};for(int j=0;j<4;j++)sum[j]+=a[i][j]*x[i];}
        for(int pass=0;pass<180;pass++)for(int i=0;i<count;i++){if(all.get(i).unit().equals("stuk"))continue;double numerator=0,denominator=0;for(int j=0;j<4;j++){double w=1/(scale[j]*scale[j]);numerator+=a[i][j]*(goal[j]-sum[j])*w;denominator+=a[i][j]*a[i][j]*w;}double regularizer=.002/(original[i]*original[i]);double next=Math.max(low[i],Math.min(high[i],x[i]+(numerator+regularizer*(original[i]-x[i]))/(denominator+regularizer)));for(int j=0;j<4;j++)sum[j]+=a[i][j]*(next-x[i]);x[i]=next;}
        for(int i=0;i<count;i++)x[i]=Math.max(low[i],Math.min(high[i],Math.rint(x[i])));
        var result=new ArrayList<MealPlanController.Meal>();int n=0;for(var meal:meals){var foods=new ArrayList<MealPlanController.Food>();for(var f:meal.foods())foods.add(withAmount(f,x[n++]));result.add(new MealPlanController.Meal(meal.name(),foods,meal.instructions()));}return List.copyOf(result);
    }
}
