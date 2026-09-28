import java.io.IOException;
import java.util.Scanner;

public class App {
    public static void main(String[] args) {
        WorkoutLibrary library = new WorkoutLibrary();
        WorkoutFileManager fileManager = new WorkoutFileManager();

        try {
            fileManager.loadWorkouts(library);
        } catch (IOException e) {
            System.err.println("Could not load workouts: " + e.getMessage());
            System.err.println("The application will close without saving. workouts.txt was not changed.");
            return;
        }

        try (Scanner scanner = new Scanner(System.in)) {
            UserInterface ui = new UserInterface(scanner, library, fileManager);
            ui.start();

            try {
                fileManager.saveWorkouts(library);
            } catch (IOException e) {
                System.err.println("Could not save workouts: " + e.getMessage());
            }
        }
    }
}

