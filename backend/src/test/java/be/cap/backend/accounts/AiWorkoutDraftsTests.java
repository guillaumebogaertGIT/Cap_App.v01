package be.cap.backend.accounts;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AiWorkoutDraftsTests {
    private final AiWorkoutDrafts drafts=new AiWorkoutDrafts(null);
    private final tools.jackson.databind.json.JsonMapper json=tools.jackson.databind.json.JsonMapper.builder().build();
    private String proposal(Map<String,Object> exercise) throws Exception {return json.writeValueAsString(Map.of("status","draft","message","Controleer dit concept.","name","Test","description","","workoutType","full-body","exercises",List.of(exercise)));}
    @Test void rejectsUnknownExercisesExtraFieldsAndInvalidPrescriptions() throws Exception {
        var valid=new HashMap<String,Object>(Map.of("name","Squat","sets",3,"reps",10,"restSeconds",60));
        assertTrue(drafts.validate(proposal(valid),List.of("Squat")).containsKey("workout"));
        assertThrows(IllegalArgumentException.class,()->drafts.validate(proposal(valid),List.of("Press")));
        for(var entry:Map.<String,Object>of("sets",0,"reps",51,"restSeconds",601,"targetKg",100,"athleteId","someone").entrySet()){
            var bad=new HashMap<>(valid);bad.put(entry.getKey(),entry.getValue());assertThrows(IllegalArgumentException.class,()->drafts.validate(proposal(bad),List.of("Squat")));
        }
        var decimal=new HashMap<>(valid);decimal.put("sets",2.5);assertThrows(IllegalArgumentException.class,()->drafts.validate(proposal(decimal),List.of("Squat")));
    }
    @Test void clarificationHasNoWorkoutAndMalformedProviderTextFails() throws Exception {
        String question=json.writeValueAsString(Map.of("status","clarification","message","Welk materiaal is beschikbaar?","name","","description","","workoutType","other","exercises",List.of()));
        assertEquals(Map.of("answer","Welk materiaal is beschikbaar?"),drafts.validate(question,List.of("Squat")));
        assertThrows(Exception.class,()->drafts.validate("not JSON",List.of("Squat")));
    }
}
