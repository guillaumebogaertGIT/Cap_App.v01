# CAP App

A beginner-friendly Java project for building and managing workouts. The current version runs as a console application and focuses on a small working MVP before adding larger fitness, coaching, nutrition, basketball, or AI features.

## Current features

- Add a workout by name
- Add multiple exercises to a workout
- Store exercise sets, reps, and tempo
- View all saved workouts
- Find a workout by name (case-insensitive)
- Remove a workout by name
- Run the app through a simple console menu

## Project structure

- `src/App.java` — starts the application and launches the user interface
- `src/UserInterface.java` — handles the console menu and user input
- `src/WorkoutLibrary.java` — stores workouts and handles add/find/remove operations
- `src/Workout.java` — represents one workout and its exercises
- `src/Exercise.java` — represents an exercise with sets, reps, and tempo
- `src/Athlete.java` — early athlete model for future expansion

## Run the app

Open the project in VS Code with a Java JDK installed, then run `src/App.java`.

From the menu you can add, view, find, and remove workouts.

## Current stage

This project is intentionally small while the core Java design is being learned and tested. Data is currently stored only in memory, so workouts disappear when the program closes.

Good next steps are input validation, automated tests for the workout library, and eventually persistent storage before moving to a graphical or web interface.
