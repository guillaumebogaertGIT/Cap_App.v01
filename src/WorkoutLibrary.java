import java.util.ArrayList;

public class WorkoutLibrary {
    private ArrayList<Workout> workouts;

    public WorkoutLibrary() {
        this.workouts = new ArrayList<Workout>();
    }

    public void addWorkout(Workout workout) {
        this.workouts.add(workout);
    }

    public void removeWorkout(String name) {
        Workout workout = findWorkout(name);
        if (workout != null) {
            this.workouts.remove(workout);
        }
    }

    public void printWorkouts() {
        if (this.workouts.isEmpty()) {
            System.out.println("No workouts available.");
            return;
        }

        for (Workout workout : this.workouts) {
            System.out.println(workout);
        }
    }

    public Workout findWorkout(String name) {
        for (Workout workout : this.workouts) {
            if (workout.getName().equalsIgnoreCase(name)) {
                return workout;
            }
        }
        return null;
    }
}


