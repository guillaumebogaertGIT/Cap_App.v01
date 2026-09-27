import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Scanner;

public class WorkoutFileManager {

    public void saveWorkouts(WorkoutLibrary library) throws IOException {
        try (FileWriter filewriter = new FileWriter("workouts.txt")) {

            for (Workout workout : library.getWorkouts()) {
                filewriter.write("WORKOUT: " + workout.getName() + "\n");

                for (Exercise exercise : workout.getExercises()) {
                    filewriter.write("EXERCISE: " + exercise.getName() + ", " + exercise.getSets()
                            + ", " + exercise.getReps() + ", " + exercise.getTempo() + "\n");
                }
            }
        }
    }

    public void loadWorkouts(WorkoutLibrary library) throws IOException {
        File file = new File("workouts.txt");
        if (!file.exists()) {
            return; //no files to load.
        }

        // Only publish the loaded workouts after every line has been read successfully.
        WorkoutLibrary loadedLibrary = new WorkoutLibrary();
        try (Scanner fileScanner = new Scanner(file)) {
            Workout currentWorkout = null;
            int lineNumber = 0;

            while (fileScanner.hasNextLine()) {
                String line = fileScanner.nextLine();
                lineNumber++;
                if (line.trim().isEmpty()) {
                    continue;
                }

                if (line.startsWith("WORKOUT: ")) {
                    String workoutName = line.substring(9);

                    currentWorkout = new Workout(workoutName);
                    loadedLibrary.addWorkout(currentWorkout);
                }
                else if (line.startsWith("EXERCISE: ")) {
                    if (currentWorkout == null) {
                        throw new IOException(
                                "Cannot load exercise before a workout: " + line);
                    }

                    String exerciseData = line.substring(10);

                    String[] parts = exerciseData.split(", ", -1);
                    if (parts.length != 4) {
                        throw new IOException("Line " + lineNumber
                                + ": expected exercise name, sets, reps, and tempo.");
                    }
                    String name = parts[0];
                    int sets;
                    int reps;
                    try {
                        sets = Integer.parseInt(parts[1]);
                        reps = Integer.parseInt(parts[2]);
                    } catch (NumberFormatException e) {
                        throw new IOException("Line " + lineNumber
                                + ": sets and reps must be whole numbers.", e);
                    }
                    String tempo = parts[3];

                    Exercise exercise = new Exercise(name, sets, reps, tempo);
                    currentWorkout.addExercise(exercise);
                } else {
                    throw new IOException("Unrecognized format at line " + lineNumber
                            + ". Older display-format files must be backed up and converted first. "
                            + "Expected WORKOUT: name or EXERCISE: name, sets, reps, tempo.");
                }
            }
            // Scanner can stop reading after an I/O error without throwing it.
            if (fileScanner.ioException() != null) {
                throw fileScanner.ioException();
            }
        }
        for (Workout workout : loadedLibrary.getWorkouts()) {
            library.addWorkout(workout);
        }
    }
}
