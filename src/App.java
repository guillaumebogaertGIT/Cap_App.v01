public class App {
    public static void main(String[] args) {

        WorkoutLibrary library = new WorkoutLibrary();

        // create workout
        Workout workout = new Workout("Upper Boddy");

        Exercise exercise = new Exercise("Bench press", 3 , 5, "4-1-3-2");
        workout.addExercise(exercise);
        Exercise exercise2 = new Exercise("Pull up", 3, 8, "4-1-0-1");
        workout.addExercise(exercise2);

        // add it
        library.addWorkout(workout);

        // print library
        library.printWorkouts();
    }

    
}