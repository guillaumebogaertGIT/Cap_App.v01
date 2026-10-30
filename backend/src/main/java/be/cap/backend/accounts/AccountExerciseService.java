package be.cap.backend.accounts;

import be.cap.backend.ExerciseStore;
import java.io.IOException;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
@Profile("accounts")
@Transactional(rollbackFor=Exception.class)
public class AccountExerciseService {
    private final JdbcTemplate jdbc;
    private final JsonMapper mapper=JsonMapper.builder().build();
    private final List<ExerciseStore.Exercise> seed;
    public AccountExerciseService(JdbcTemplate jdbc) throws IOException {
        this.jdbc=jdbc;
        try(var source=ExerciseStore.class.getResourceAsStream("/exercise-catalog.json")) {
            if(source==null)throw new IOException("Exercise catalog resource missing.");
            seed=List.of(mapper.readValue(source,ExerciseStore.Exercise[].class));
        }
    }
    private ExerciseStore store(boolean lock) throws IOException {
        String json=jdbc.queryForObject("SELECT payload FROM exercise_state WHERE id=1"+(lock?" FOR UPDATE":""),String.class);
        return new ExerciseStore(seed,List.of(mapper.readValue(json,ExerciseStore.Exercise[].class)),state->jdbc.update("UPDATE exercise_state SET payload=? WHERE id=1",mapper.writeValueAsString(state)));
    }
    public List<ExerciseStore.Exercise> list(AccountService.Account user) throws IOException {if(!user.role().equals("ATHLETE"))new StaffPolicy(jdbc).require(user,StaffPolicy.Permission.WORKOUT_VIEW);return store(false).list(); }
    public ExerciseStore.Exercise save(String id,ExerciseStore.Exercise input,AccountService.Account user) throws IOException {new StaffPolicy(jdbc).require(user,StaffPolicy.Permission.WORKOUT_TEMPLATE_MANAGE);var store=store(true);return id==null?store.create(input):store.update(id,input); }
    public ExerciseStore.Exercise archive(String id,boolean archived,AccountService.Account user) throws IOException {new StaffPolicy(jdbc).require(user,StaffPolicy.Permission.WORKOUT_TEMPLATE_MANAGE);return store(true).archive(id,archived); }
}
