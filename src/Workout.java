import java.util.ArrayList;

public class Workout {
    private String name;
    private ArrayList<Exercise> exercises;

public Workout(String name) {
    this.name = name; 
    this.exercises =  new ArrayList<Exercise>();
}
public void addExercise(Exercise exercise){
    this.exercises.add(exercise);
}
public ArrayList<Exercise> getExercises() {
    return this.exercises;
}
public String getName(){
    return this.name;
}
public String toString() {
    String output = this.name;

    for (Exercise exercise : this.exercises) {
        output = output + "\n" + exercise;
    }

    return output;
}
}
