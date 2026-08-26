public class Exercise {
    private String name;
    private int sets;
    private int reps;
    private String tempo;

    public Exercise(String name, int sets, int reps, String tempo) {
        this.name = name;
        this.sets = sets;
        this.reps = reps;
        this.tempo = tempo;
    }

    public String getName() {
        return this.name;
    }

    public int getSets() {
        return this.sets;
    }

    public int getReps() {
        return this.reps;
    }

    public String getTempo() {
        return this.tempo;
    }

    @Override
    public String toString() {
        return this.name
                + ": " + this.sets
                + " sets of " + this.reps
                + " reps, tempo " + this.tempo;
    }
}
