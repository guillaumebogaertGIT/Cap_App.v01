public class Athlete {
    private String name;

    public Athlete(String name) {
        this.name = name;
    }

    public String getname() {
        return this.name ;
    }

    @Override
    public String toString() {
        return this.name;
    }



}
