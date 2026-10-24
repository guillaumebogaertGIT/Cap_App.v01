package be.cap.backend;

import java.util.List;

public record Workout(String id, String name, String description, List<Exercise> exercises,
                      String athleteId, String assignedDate, String category, String workoutType) {
    public Workout(String id, String name, String description, List<Exercise> exercises, String athleteId, String assignedDate, String category) {
        this(id, name, description, exercises, athleteId, assignedDate, category, "other");
    }
    public Workout(String id, String name, String description, List<Exercise> exercises, String athleteId, String assignedDate) {
        this(id, name, description, exercises, athleteId, assignedDate, "");
    }
    public record SetTarget(int reps, String loadMode, Double loadValue, Double targetKg, Double referenceKg) {}
    public record Exercise(String name, int sets, int reps, String loadMode, Double loadValue,
                           Double targetKg, Double referenceKg, Integer restSeconds, String tempo, String supersetGroup, List<SetTarget> setPlan) {
        public Exercise(String name, int sets, int reps, String loadMode, Double loadValue, Double targetKg, Double referenceKg, Integer restSeconds, String tempo, String supersetGroup) {
            this(name, sets, reps, loadMode, loadValue, targetKg, referenceKg, restSeconds, tempo, supersetGroup, null);
        }
        public Exercise(String name, int sets, int reps, String loadMode, Double loadValue, Double targetKg, Double referenceKg, Integer restSeconds, String tempo) {
            this(name, sets, reps, loadMode, loadValue, targetKg, referenceKg, restSeconds, tempo, null);
        }
        public Exercise(String name, int sets, int reps, String loadMode, Double loadValue, Double targetKg, Double referenceKg) {
            this(name, sets, reps, loadMode, loadValue, targetKg, referenceKg, null, null);
        }
        public Exercise(String name, int sets, int reps) { this(name, sets, reps, "none", null, null, null); }
    }
}
