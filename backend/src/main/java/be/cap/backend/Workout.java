package be.cap.backend;

import java.util.List;

public record Workout(String id, String name, String description, List<Exercise> exercises,
                      String athleteId, String assignedDate) {
    public record Exercise(String name, int sets, int reps, String loadMode, Double loadValue,
                           Double targetKg, Double referenceKg) {
        public Exercise(String name, int sets, int reps) { this(name, sets, reps, "none", null, null, null); }
    }
}
