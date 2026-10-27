package be.cap.backend.accounts;

import be.cap.backend.*;
import java.io.IOException;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Profile("accounts")
@RequestMapping("/api/exercises")
class AccountExerciseController {
    private final AccountExerciseService service;
    AccountExerciseController(AccountExerciseService service) {this.service=service;}
    private ExerciseStore.Exercise definition(ExerciseController.ExerciseInput input) {
        return new ExerciseStore.Exercise(null,input.name(),input.region(),input.category(),input.subcategory(),input.instructions(),input.notes(),false);
    }
    @GetMapping List<ExerciseStore.Exercise> list() throws IOException {return service.list();}
    @PostMapping @ResponseStatus(HttpStatus.CREATED) ExerciseStore.Exercise create(@RequestBody ExerciseController.ExerciseInput input) throws IOException {return service.save(null,definition(input));}
    @PutMapping("/{id}") ExerciseStore.Exercise update(@PathVariable String id,@RequestBody ExerciseController.ExerciseInput input) throws IOException {return service.save(id,definition(input));}
    @PatchMapping("/{id}/archive") ExerciseStore.Exercise archive(@PathVariable String id,@RequestBody ExerciseController.ArchiveRequest input) throws IOException {
        if(input.archived()==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Kies archiveren of herstellen.");return service.archive(id,input.archived());
    }
}
