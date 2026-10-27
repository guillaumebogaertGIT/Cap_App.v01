package be.cap.backend;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

class ExerciseStoreTests {
    @TempDir Path directory;

    private ExerciseStore.Exercise input(String name) {
        return new ExerciseStore.Exercise("ignored-client-id", name, "Lower Body", "Squat", "Bilateral", "Brace first", "", false);
    }

    @Test void seedLoadsWithoutWritingAndOnlyOverridesArePersisted() throws Exception {
        Path file = directory.resolve("exercises.json");
        ExerciseStore store = new ExerciseStore(file.toString());
        assertEquals(1078, store.list().size());
        assertFalse(Files.exists(file));
        var added = store.create(input("CAP Test Squat"));
        assertNotEquals("ignored-client-id", added.id());
        assertEquals(1, JsonMapper.builder().build().readTree(Files.readString(file)).size());
        var renamed = store.update(added.id(), input("  CAP Renamed Squat  "));
        assertEquals("CAP Renamed Squat", renamed.name());
        assertEquals(added.id(), renamed.id());
        assertTrue(store.archive(added.id(), true).archived());
        ExerciseStore reloaded = new ExerciseStore(file.toString());
        assertEquals(1079, reloaded.list().size());
        assertTrue(reloaded.list().stream().filter(e -> e.id().equals(added.id())).findFirst().orElseThrow().archived());
        assertFalse(reloaded.archive(added.id(), false).archived());
        assertFalse(new ExerciseStore(file.toString()).list().getLast().archived());
    }

    @Test void importedChangesPersistWithoutRewritingSeedOrOtherEntries() throws Exception {
        String filename = directory.resolve("exercises.json").toString();
        ExerciseStore store = new ExerciseStore(filename);
        var original = store.list().getFirst();
        var untouched = store.list().get(1);
        store.update(original.id(), input("CAP Imported Override"));
        store.archive(original.id(), true);
        var reloaded = new ExerciseStore(filename);
        assertEquals(1078, reloaded.list().size());
        assertEquals("CAP Imported Override", reloaded.list().getFirst().name());
        assertTrue(reloaded.list().getFirst().archived());
        assertEquals(untouched, reloaded.list().get(1));
        assertEquals(original, new ExerciseStore(directory.resolve("fresh.json").toString()).list().getFirst());
    }

    @Test void invalidOrDuplicateInputNeverChangesStoredCatalog() throws Exception {
        Path file = directory.resolve("exercises.json");
        ExerciseStore store = new ExerciseStore(file.toString());
        assertThrows(ResponseStatusException.class, () -> store.create(input(" ")));
        assertThrows(ResponseStatusException.class, () -> store.create(input("x".repeat(101))));
        assertThrows(ResponseStatusException.class, () -> store.create(new ExerciseStore.Exercise(null, "Bad", "invalid", "Squat", "", "", "", false)));
        assertThrows(ResponseStatusException.class, () -> store.create(new ExerciseStore.Exercise(null, "Bad", null, "Squat", "", "", "", false)));
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class, () -> store.archive("missing", true)).getStatusCode());
        assertFalse(Files.exists(file));
        var exercise = store.create(input("CAP Unique Squat"));
        String before = Files.readString(file);
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
            () -> store.create(input(" cap   unique squat "))).getStatusCode());
        store.archive(exercise.id(), true);
        assertThrows(ResponseStatusException.class, () -> store.create(input("CAP Unique Squat")));
        store.archive(exercise.id(), false);
        assertEquals(before, Files.readString(file));
    }

    @Test void failedWriteLeavesMemoryAndExistingDataUntouched() throws Exception {
        Path target = directory.resolve("blocked.json");
        ExerciseStore store = new ExerciseStore(target.toString());
        Files.createDirectory(target);
        Files.writeString(target.resolve("sentinel"), "keep");
        var before = store.list();
        assertThrows(IOException.class, () -> store.create(input("CAP Failing Save")));
        assertEquals(before, store.list());
        assertEquals("keep", Files.readString(target.resolve("sentinel")));
        try (var files = Files.list(directory)) { assertEquals(1, files.count()); }
    }
}
