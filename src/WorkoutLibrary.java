import java.util.ArrayList;

public class WorkoutLibrary {
    private ArrayList<Workout> workouts;

public WorkoutLibrary() {
    this.workouts = new ArrayList<Workout>();
}
public void addWorkout(Workout workout) {
    this.workouts.add(workout);
}
public void printWorkouts() {
    for (Workout workout : this.workouts) {
        System.out.println(workout);
    }
   
}
}
