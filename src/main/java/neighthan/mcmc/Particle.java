package neighthan.mcmc;

/**
 *
 */
public class Particle {
    private double x;
    private double y;

    public Particle(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public double x() {
        return x;
    }

    public void x(int x) {
        this.x = x;
    }

    public double y() {
        return y;
    }

    public void y(int y) {
        this.y = y;
    }

    /**
     * @param other particle to which to compute distance
     * @return Manhattan distance between this and other
     */
    public double manhattanDistance(Particle other) {
        double distance = 0;
        distance += Math.abs(x - other.x);
        distance += Math.abs(y - other.y);
        return distance;
    }

    /**
     * @param other particle to which to compute distance
     * @return Manhattan distance between this and other
     */
    public double squaredManhattanDistance(Particle other) {
        double distance = 0;
        distance += Math.pow(x - other.x, 2);
        distance += Math.pow(y - other.y, 2);
        return distance;
    }

    @Override
    public String toString() {
        return "{" + x + ", " + y + "}";
    }
}
