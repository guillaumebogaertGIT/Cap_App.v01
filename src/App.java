import java.util.Scanner;

public class App {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        WorkoutLibrary library = new WorkoutLibrary();

        UserInterface ui = new UserInterface(scanner, library);
        ui.start();
    }
}

