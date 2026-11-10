package be.cap.backend.accounts;

import be.cap.backend.Workout;
import java.util.*;
import java.nio.charset.StandardCharsets;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Ephemeral proposals only: this component never persists or assigns a workout. */
@Component @Profile("accounts")
public class AiWorkoutDrafts {
    private final AccountExerciseService catalog;
    private final JsonMapper mapper=JsonMapper.builder().build();
    public AiWorkoutDrafts(AccountExerciseService catalog){this.catalog=catalog;}
    public List<String> candidates(AccountService.Account user,String question) throws Exception {
        var words=Arrays.stream(question.toLowerCase(Locale.ROOT).split("[^\\p{L}]+")) .filter(w->w.length()>3).toList();
        var exercises=new ArrayList<>(catalog.list(user).stream().filter(e->!e.archived()).toList());
        exercises.sort(Comparator.comparingInt((be.cap.backend.ExerciseStore.Exercise e)->{
            String text=(e.name()+" "+e.region()+" "+e.category()+" "+e.subcategory()).toLowerCase(Locale.ROOT);
            return -((int)words.stream().filter(text::contains).count());
        }).thenComparing(e->e.name()));
        // Round-robin movement categories: a bounded catalogue should still cover a full body session.
        var groups=new LinkedHashMap<String,ArrayDeque<String>>();
        for(var e:exercises)groups.computeIfAbsent(e.category(),key->new ArrayDeque<>()).add(e.name());
        var names=new ArrayList<String>();int bytes=0;boolean remaining=true;
        while(remaining&&names.size()<48){remaining=false;for(var group:groups.values()){
            if(group.isEmpty())continue;remaining=true;String name=group.removeFirst();int size=name.getBytes(StandardCharsets.UTF_8).length;
            if(name.isBlank()||size>180||names.contains(name)||bytes+size>6000)continue;names.add(name);bytes+=size;if(names.size()==48)break;
        }}
        if(names.isEmpty())throw new IllegalStateException("No active exercises");return List.copyOf(names);
    }
    private String text(JsonNode node,String key,int max){var value=node.path(key);if(!value.isString()||value.asText().length()>max)throw new IllegalArgumentException("Invalid draft text");return value.asText();}
    private int number(JsonNode node,String key,int min,int max){var value=node.path(key);if(!value.isIntegralNumber()||!value.canConvertToInt()||value.asInt()<min||value.asInt()>max)throw new IllegalArgumentException("Invalid draft number");return value.asInt();}
    private void fields(JsonNode node,Set<String> expected){if(!node.isObject()||!node.propertyNames().equals(expected))throw new IllegalArgumentException("Unexpected draft fields");}
    public Map<String,Object> validate(String output,List<String> names) throws Exception {
        var root=mapper.readTree(output);fields(root,Set.of("status","message","name","description","workoutType","exercises"));
        String status=text(root,"status",20),message=text(root,"message",8000),name=text(root,"name",100),description=text(root,"description",1000),type=text(root,"workoutType",30);
        if(message.isBlank()||!Set.of("other","lower-body","upper-body","full-body","conditioning","wod").contains(type)||!root.path("exercises").isArray())throw new IllegalArgumentException("Invalid draft");
        var rows=root.path("exercises");if(Set.of("answer","clarification").contains(status)&&rows.isEmpty())return Map.of("answer",message);
        if(!status.equals("draft")||name.isBlank()||rows.isEmpty()||rows.size()>8)throw new IllegalArgumentException("Invalid draft");
        var exercises=new ArrayList<Workout.Exercise>();
        for(var row:rows){fields(row,row.has("rir")?Set.of("name","sets","reps","restSeconds","rir"):Set.of("name","sets","reps","restSeconds"));String exercise=text(row,"name",180);if(!names.contains(exercise))throw new IllegalArgumentException("Unknown exercise");
            Integer rir=row.hasNonNull("rir")?number(row,"rir",0,10):null;
            exercises.add(new Workout.Exercise(exercise,number(row,"sets",1,8),number(row,"reps",1,50),"none",null,null,null,number(row,"restSeconds",0,600),null,null,null,rir));}
        return Map.of("answer",message,"workout",new Workout(null,name,description,List.copyOf(exercises),null,null,"",type));
    }
}
