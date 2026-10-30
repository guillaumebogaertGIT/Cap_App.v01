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
    private final AccountService accounts;
    AccountExerciseController(AccountExerciseService service,AccountService accounts) {this.service=service;this.accounts=accounts;}
    private ExerciseStore.Exercise definition(ExerciseController.ExerciseInput input) {
        return new ExerciseStore.Exercise(null,input.name(),input.region(),input.category(),input.subcategory(),input.instructions(),input.notes(),false);
    }
    @GetMapping List<ExerciseStore.Exercise> list(java.security.Principal p) throws IOException {return service.list(accounts.account(p.getName()));}
    @PostMapping @ResponseStatus(HttpStatus.CREATED) ExerciseStore.Exercise create(java.security.Principal p,@RequestBody ExerciseController.ExerciseInput input) throws IOException {return service.save(null,definition(input),accounts.account(p.getName()));}
    @PutMapping("/{id}") ExerciseStore.Exercise update(java.security.Principal p,@PathVariable String id,@RequestBody ExerciseController.ExerciseInput input) throws IOException {return service.save(id,definition(input),accounts.account(p.getName()));}
    @PatchMapping("/{id}/archive") ExerciseStore.Exercise archive(java.security.Principal p,@PathVariable String id,@RequestBody ExerciseController.ArchiveRequest input) throws IOException {
        if(input.archived()==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Kies archiveren of herstellen.");return service.archive(id,input.archived(),accounts.account(p.getName()));
    }
}
