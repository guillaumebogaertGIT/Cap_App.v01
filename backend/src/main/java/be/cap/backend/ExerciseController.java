package be.cap.backend;

import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@org.springframework.context.annotation.Profile("!accounts")
@RequestMapping("/api/exercises")
@CrossOrigin(origins = "http://127.0.0.1:5500")
public class ExerciseController {
    public record ExerciseInput(String name, String region, String category, String subcategory,
                                String instructions, String notes) {
        ExerciseStore.Exercise definition() {
            return new ExerciseStore.Exercise(null, name, region, category, subcategory, instructions, notes, false);
        }
    }
    public record ArchiveRequest(Boolean archived) {}
    private final ExerciseStore exercises;

    public ExerciseController(ExerciseStore exercises) {
        this.exercises = exercises;
    }

    @GetMapping
    public List<ExerciseStore.Exercise> list() {
        return exercises.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ExerciseStore.Exercise create(@RequestBody ExerciseInput input) throws IOException {
        return exercises.create(input.definition());
    }

    @PutMapping("/{id}")
    public ExerciseStore.Exercise update(@PathVariable String id, @RequestBody ExerciseInput input) throws IOException {
        return exercises.update(id, input.definition());
    }

    @PatchMapping("/{id}/archive")
    public ExerciseStore.Exercise archive(@PathVariable String id, @RequestBody ArchiveRequest input) throws IOException {
        if (input.archived() == null) throw new org.springframework.web.server.ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Kies archiveren of herstellen.");
        return exercises.archive(id, input.archived());
    }
}
