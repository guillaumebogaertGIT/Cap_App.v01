import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Scanner;

public class WorkoutFileManager {

    public void saveWorkouts(WorkoutLibrary library) throws IOException{
        FileWriter filewriter = new FileWriter("workouts.txt");

        for (Workout workout : library.getWorkouts())  {
            filewriter.write("WORKOUT: " + workout.getName() + "\n");

            for (Exercise exercise : workout.getExercises()) {
                filewriter.write("EXERCISE: " + exercise.getName() + ", " + exercise.getSets()+ ", "+ exercise.getReps()+ ", " + exercise.getTempo() + "\n");
            }
        }
        filewriter.close();
    }

    public void loadWorkouts(WorkoutLibrary library) throws IOException {
        File file = new File("workouts.txt");
        if (!file.exists()) {
            return; //no files to load. 
        }

        Scanner fileScanner = new Scanner(file);

        Workout currentWorkout = null;

        while (fileScanner.hasNextLine()) {
            String line = fileScanner.nextLine();

            if (line.startsWith("WORKOUT: ")) {
                String workoutName = line.substring(9);

                currentWorkout = new Workout(workoutName);
                library.addWorkout(currentWorkout);
            } 
            else if (line.startsWith("EXERCISE: ")) {
                String exerciseData = line.substring(10);

                String[] parts = exerciseData.split(", ");
                String name = parts[0];
                int sets = Integer.valueOf(parts[1]);
                int reps = Integer.valueOf(parts[2]);
                String tempo = parts[3];

                Exercise exercise = new Exercise (name, sets, reps, tempo);

            }

            

        }


    }
}
