public class App {
    public static void main(String[] args) throws Exception {
       Athlete athlete = new Athlete ("Guillaume");
       Exercise exercise = new Exercise ("Bench press", 3, 6, "3-1-1-0");

       System.out.println(athlete);
       System.out.println(exercise);
    }
}
