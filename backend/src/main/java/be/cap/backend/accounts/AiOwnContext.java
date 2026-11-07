package be.cap.backend.accounts;

import be.cap.backend.*;
import java.time.*;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** Explicit own-data projection. No target account parameter, identifiers, notes or writes. */
@Component @Profile("accounts")
public class AiOwnContext {
    private final JdbcTemplate jdbc;private final AccountWorkoutService workouts;
    private final JsonMapper json=JsonMapper.builder().build();
    public AiOwnContext(JdbcTemplate jdbc,AccountWorkoutService workouts){this.jdbc=jdbc;this.workouts=workouts;}
    private String shortText(String value){return value==null?"":value.substring(0,Math.min(value.length(),100));}
    private Map<String,Object> planned(Workout workout){return Map.of("name",shortText(workout.name()),"date",Objects.toString(workout.assignedDate(),""),"exercises",workout.exercises().stream().limit(8).map(e->Map.of("name",shortText(e.name()),"sets",e.sets(),"reps",e.reps())).toList());}
    @Transactional(readOnly=true)
    public String read(AccountService.Account user,boolean training,boolean nutrition) throws Exception {
        return read(user,training,nutrition,false,false);
    }
    @Transactional(readOnly=true)
    public String read(AccountService.Account user,boolean training,boolean nutrition,boolean profile,boolean bookings) throws Exception {
        if(!training&&!nutrition&&!profile&&!bookings)return "";
        LocalDate today=LocalDate.now(ZoneId.of("Europe/Brussels")),monday=today.with(java.time.DayOfWeek.MONDAY);
        var data=new LinkedHashMap<String,Object>();data.put("todayBrussels",today.toString());data.put("scope","Only the signed-in user's own data; bounded snapshot, not all app records.");
        if(profile){
            var rows=jdbc.query("SELECT values_json FROM account_profile WHERE user_id=?",(r,i)->json.readValue(r.getString(1),ProfileController.Values.class),user.id());
            var values=rows.isEmpty()?null:rows.getFirst();
            data.put("profile",Map.of("name",shortText(user.name()),"goal",values==null?"":shortText(values.goal()),"experience",values==null?"":shortText(values.experience())));
            data.put("profileLimits","Name, saved goal and experience only. No birth date, sex, body measurements, contact details, photo or medical information. Empty means not provided. Profile values are current app records, not permanent AI memory.");
        }
        if(bookings){
            data.put("upcomingOwnBookings",jdbc.query("SELECT s.title,s.session_date,s.start_time,s.end_time,s.session_type FROM session_booking b JOIN club_session s ON s.id=b.session_id WHERE b.athlete_id=? AND b.cancelled=FALSE AND s.session_date BETWEEN ? AND ? AND (s.session_date>? OR s.end_time>?) ORDER BY s.session_date,s.start_time LIMIT 8",(r,i)->Map.of("title",shortText(r.getString(1)),"date",r.getDate(2).toLocalDate().toString(),"start",r.getTime(3).toLocalTime().toString(),"end",r.getTime(4).toLocalTime().toString(),"type",r.getString(5)),user.id(),today,today.plusDays(14),today,LocalTime.now(ZoneId.of("Europe/Brussels"))));
            data.put("bookingLimits","At most 8 own active bookings in the next 14 days. No attendee lists, private session notes, payments or availability. Cannot book, cancel or change anything.");
        }
        if(training){
            // Coaches' roster/library endpoints are deliberately NOT used as personal context.
            data.put("plannedWorkoutsThisWeek",user.role().equals("ATHLETE")?workouts.assignments(user).stream().filter(a->{String day=a.planned().assignedDate(),week=a.weekStart();return (day!=null&&day.compareTo(monday.toString())>=0&&day.compareTo(monday.plusDays(6).toString())<=0)||monday.toString().equals(week);}).limit(3).map(a->planned(a.planned())).toList():List.of());
            var history=jdbc.query("SELECT payload FROM athlete_training WHERE athlete_id=?",(r,i)->json.readValue(r.getString(1),TrainingStore.Session[].class),user.id());
            var recent=new ArrayList<Object>();
            if(!history.isEmpty())Arrays.stream(history.getFirst()).filter(s->user.id().toString().equals(s.athleteId())&&s.finishedAt()!=null&&s.finishedAt().isAfter(today.minusDays(14).atStartOfDay(ZoneId.of("Europe/Brussels")).toInstant())).sorted(Comparator.comparing(TrainingStore.Session::finishedAt).reversed()).limit(2).forEach(s->{
                var results=new ArrayList<Object>();for(var e:s.exercises().stream().limit(4).toList())if(e.exerciseIndex()>=0&&e.exerciseIndex()<s.planned().exercises().size())results.add(Map.of("exercise",shortText(s.planned().exercises().get(e.exerciseIndex()).name()),"sets",e.sets().stream().limit(4).map(v->{var set=new LinkedHashMap<String,Object>();set.put("reps",v.reps());if(v.kg()!=null)set.put("kg",v.kg());return set;}).toList()));
                recent.add(Map.of("name",shortText(s.planned().name()),"finishedAt",s.finishedAt().toString(),"results",results));});
            data.put("recentCompletedWorkouts14Days",recent);data.put("trainingLimits","At most 3 plans this week and 2 completed sessions, each with a limited exercise/set sample. Missing records are unknown, not proof of no training.");
        }
        if(nutrition){
            data.put("savedDailyGoals",jdbc.query("SELECT calories,protein_g,carbs_g,fat_g,water_ml FROM nutrition_target WHERE athlete_id=?",(r,i)->Map.of("calories",r.getDouble(1),"proteinG",r.getDouble(2),"carbsG",r.getDouble(3),"fatG",r.getDouble(4),"waterMl",r.getDouble(5)),user.id()));
            data.put("todayLoggedTotals",jdbc.queryForMap("SELECT COUNT(*) AS entries,COALESCE(SUM((payload::jsonb->>'calories')::numeric),0) AS calories,COALESCE(SUM((payload::jsonb->>'proteinG')::numeric),0) AS protein_g,COALESCE(SUM((payload::jsonb->>'carbsG')::numeric),0) AS carbs_g,COALESCE(SUM((payload::jsonb->>'fatG')::numeric),0) AS fat_g FROM food_diary_entry WHERE athlete_id=? AND entry_date=?",user.id(),today));
            data.put("todayFoodSample",jdbc.query("SELECT payload FROM food_diary_entry WHERE athlete_id=? AND entry_date=? ORDER BY saved_at,id LIMIT 8",(r,i)->{var e=json.readValue(r.getString(1),FoodDiaryController.Entry.class);return Map.of("food",shortText(e.name()),"meal",e.meal(),"amount",e.amount(),"unit",shortText(e.unit()));},user.id(),today));
            data.put("nutritionLimits","Today only. Totals include all logged entries; names are a sample of at most 8. No entries means no logged food, not that the person has eaten nothing. No weight, medical records or inferred diet needs.");
        }
        String encoded=json.writeValueAsString(data);if(encoded.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>6000)throw new IllegalStateException("Own context too large");return encoded;
    }
}
