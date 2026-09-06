import java.util.Scanner;

public class UserInterface {
    private Scanner scanner;
    private WorkoutLibrary library;

    public UserInterface(Scanner scanner, WorkoutLibrary library) {
        this.scanner = scanner;
        this.library = library;
    }

    public void start() {
        while (true) {
            System.out.println("\n=== Workout App ===");
            System.out.println("1. Add workout");
            System.out.println("2. View workouts");
            System.out.println("3. Find workout");
            System.out.println("4. Remove workout");
            System.out.println("5. Exit");
            System.out.print("Choose an option: ");

            String choice = this.scanner.nextLine().trim();

            if (choice.equals("5")) {
                System.out.println("Goodbye!");
                break;
            } else if (choice.equals("2")) {
                library.printWorkouts();
            } else if (choice.equals("1")) {
                createWorkout();
            } else if (choice.equals("3")) {
                findWorkout();
            } else if (choice.equals("4")) {
                removeWorkout();
            } else {
                System.out.println("Invalid option. Please try again.");
            }
        }
    }

    private void createWorkout() {
        System.out.print("Workout name: ");
        String name = this.scanner.nextLine();
        Workout workout = new Workout(name);

        System.out.print("How many exercises? ");
        int exerciseCount = Integer.parseInt(this.scanner.nextLine().trim());

        for (int i = 1; i <= exerciseCount; i++) {
            System.out.println("\nExercise " + i);
            System.out.print("Name: ");
            String exerciseName = this.scanner.nextLine();

            System.out.print("Sets: ");
            int sets = Integer.parseInt(this.scanner.nextLine().trim());

            System.out.print("Reps: ");
            int reps = Integer.parseInt(this.scanner.nextLine().trim());

            System.out.print("Tempo: ");
            String tempo = this.scanner.nextLine();

            workout.addExercise(new Exercise(exerciseName, sets, reps, tempo));
        }

        library.addWorkout(workout);
        System.out.println("Workout added successfully.");
    }

    private void findWorkout() {
        System.out.print("Workout name: ");
        String name = this.scanner.nextLine();

        Workout workout = library.findWorkout(name);

        if (workout != null) {
            System.out.println(workout);
        } else {
            System.out.println("Workout not found.");
        }
    }

    private void removeWorkout() {
        System.out.print("Workout name: ");
        String name = this.scanner.nextLine();

        library.removeWorkout(name);
        System.out.println("Workout removed if it existed.");
    }
}

