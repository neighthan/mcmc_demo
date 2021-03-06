package neighthan.mcmc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static java.lang.Math.*;


/**
 * We have a function that runs a "simulation" - it takes in parameters and returns the final particle configuration.
 * We also have a scoring function - it takes in particle configurations and returns a score.
 * We want to find the parameters that lead to the configuration which receives the highest score.
 * (Note that nothing in the simulation can be altered except the parameters passed in; it's a black box).
 *
 * Because the cost for each theta depends on a stochastic function (the 'simulation'), running multiple times
 * would give different costs for the same theta. One could get a better idea by simulating multiple times with the
 * same theta and averaging, but we assume that simulation is expensive.
 * TODO is there a better way to compute the cost without having to simulate many times?
 *
 * 4 particles that optimally are in the four corners (furthest apart);
 * 4 thetas that determine (with normal error) the particle x's and y's after simulation; can be learned to lead to
 * optimal solution
 */
public class Simulator {
    private static Logger logger;

    private static final double qStdDev = 0.1;
    private static final int nParticles = 4;
    private static final int nTheta = 4;
    private static final int xMin = 0;
    private static final int xMax = 9;
    private static final int xDim = xMax - xMin;
    private static final int yMin = 0;
    private static final int yMax = 9;
    private static final int yDim = yMax - yMin;
    private static final int nMarkovSteps = 1000000;
    private static final Random random = new Random();
    private static final List<Particle> expected = new ArrayList<>(nParticles);

    private static double[] predictedTheta;
    private static double predictedThetaCost;
    private static List<Particle> predictedParticles;

    static {

        expected.add(new Particle(xMax, yMax));
        expected.add(new Particle(xMax, yMin));
        expected.add(new Particle(xMin, yMin));
        expected.add(new Particle(xMin, yMax));
    }

    /**
     * Sets up logging level, runs MCMC, logs (at info level) results of optimizing theta for lowest cost by MCMC
     * @param args unused
     */
    public static void main(String[] args) {
        System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "INFO");
        logger = LoggerFactory.getLogger(Simulator.class);

        double[] costs = new double[nMarkovSteps];
        double[][] thetas = new double[nMarkovSteps][nTheta];
        mcmc(thetas, costs);
        logger.info("Predicted theta: {}", predictedTheta);
        logger.info("Predicted particles\n{}", predictedParticles);
        logger.info("Expected: {}", expected);
        logger.info("Predicted cost: {}", predictedThetaCost);

        double[] thetaIdeal = {xMax, yMax, xMin, yMin};
        List<Particle> particles = simulate(thetaIdeal);
        double cost = cost(particles);
        logger.info("\nIdeal case");
        logger.info("Best theta: {}", thetaIdeal);
        logger.info("Simulated particles: {}", particles);
        logger.info("Cost: {}", cost);
    }

    /**
     * Uses MCMC to determine the value for theta such that the cost of the particle configuration generated by
     * simulating with theta is minimized. The global variables predictedTheta and predictedThetaCost are mutated by
     * this method to contain these optimal values. predictedParticles is also set to the configuration that gave the
     * predicted cost. The "time series" data across steps is written to the given arrays.
     * @param thetas array to fill with the theta values for each step; must have dimensions nMarkovSteps by nTheta
     *               (where nTheta is the number of different parameters for the simulation)
     * @param costs array to fill with the cost associated with the theta at each time step
     */
    private static void mcmc(double[][] thetas, double[] costs) {
        thetas[0] = predictedTheta = new double[] {xDim / 2, xDim / 2, yDim / 2, yDim / 2};
        costs[0] = predictedThetaCost = cost(simulate(predictedTheta));
        for (int step = 0; step < nMarkovSteps - 1; step++) {
            // for many steps, comment out logging when not in use
            logger.debug("\nStep {}", step);
            double[] thetaProposal = proposeTheta(thetas[step]);
            List<Particle> particles = simulate(thetaProposal);
            double newCost = cost(particles);
            logger.debug("Proposed theta: {}", thetaProposal);
            logger.debug("Particles = {}", particles);
            logger.debug("oldCost = {}", costs[step]);
            logger.debug("newCost = {}", newCost);
            logger.debug("prob = {}", prob(newCost, costs[step]));
            double u = random.nextDouble();
            if (u < prob(newCost, costs[step])) {
                thetas[step + 1] = thetaProposal;
                costs[step + 1] = newCost;
                if (newCost < predictedThetaCost) {
                    predictedTheta = thetaProposal;
                    predictedThetaCost = newCost;
                    predictedParticles = particles;
                }
            } else {
                thetas[step + 1] = thetas[step];
                costs[step + 1] = costs[step];
            }
        }
    }

    /**
     * "Simulates" particles given theta and returns their "resting configuration".
     *   This function is meant to be a black box and represents a real (and expensive) simulation function.
     *   Thus any strategy to do less simulations is better.
     * @param theta current simulation parameters
     * @return a particle configuration generated by simulating given theta
     */
    private static List<Particle> simulate(double[] theta) {
        final List<Particle> particles = new ArrayList<>();
        final double stdDev = .75;
        for (int i = 0; i < nParticles; i++) {
            double xMean = theta[i];
            double yMean = i + 1 < theta.length ? theta[i + 1] : theta[0]; // cycle back
            particles.add(new Particle(min(max(random.nextGaussian() * stdDev + xMean, xMin), xMax),
                    min(max(random.nextDouble() * stdDev + yMean, yMin), yMax)));
        }
        return particles;
    }

    /**
     * Computes the cost of a particle configuration as the Manhattan distance between each particle and the
     * expected position of that particle (note that the particles _are_ ordered; the same particles in a different
     * order will give a different cost)
     * @param particles current particle configuration
     * @return (non negative) cost of the given configuration
     */
    private static double cost(List<Particle> particles) {
        double cost = 0;
        for (int i = 0; i < nParticles; i++) {
            cost += expected.get(i).manhattanDistance(particles.get(i));
        }
        return cost;
    }

    /**
     * Computes the probability ratio between the proposed theta and the current theta.
     * An exponential distribution is used (proposal is symmetric; Metropolis without Hastings)
     * @param newCost cost of the particle configuratino from the proposed theta
     * @param oldCost cost of the particle configuration from the current theta
     * @return probability ratio
     */
    private static double prob(double newCost, double oldCost) {
        return Math.exp(-newCost + oldCost);
    }

    /**
     * Generates a proposal for a new theta where each element of the new theta is pulled from a normal distribution
     * centered around the corresponding element of the old theta (standard deviation is qStdDev global)
     * @param theta current theta values
     * @return new theta proposal
     */
    private static double[] proposeTheta(double[] theta) {
        double[] proposal = new double[theta.length];
        double thetaMin = (xMin + yMin) / 2.;
        double thetaMax = (xMax + yMax) / 2.;
        for (int i = 0; i < theta.length; i++) {
            proposal[i] = min(max(random.nextGaussian() * qStdDev + theta[i], thetaMin), thetaMax);
        }
        return proposal;
    }
}
