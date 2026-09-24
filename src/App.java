import java.io.IOException;
import java.util.Scanner;

public class App {
    public static void main(String[] args) throws IOException{
        Scanner scanner = new Scanner(System.in);

        WorkoutLibrary library = new WorkoutLibrary();
        WorkoutFileManager fileManager = new WorkoutFileManager();

        UserInterface ui = new UserInterface(scanner, library);
        ui.start();

        fileManager.saveWorkouts(library);
    }
}

