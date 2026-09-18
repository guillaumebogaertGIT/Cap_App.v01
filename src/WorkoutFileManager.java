import java.io.FileWriter;
import java.io.IOException;

public class WorkoutFileManager {

    public void saveWorkouts(WorkoutLibrary library) throws IOException{
        FileWriter filewriter = new FileWriter("workouts.txt");

        for (Workout workout : library.getWorkouts())  {
            filewriter.write(workout.toString() + "\n");
        }

    }
}
