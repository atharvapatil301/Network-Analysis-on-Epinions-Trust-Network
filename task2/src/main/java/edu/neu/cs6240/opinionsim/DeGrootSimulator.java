package edu.neu.cs6240.opinionsim;

import java.io.Serializable;
import java.util.*;

/**
 * Pure Java implementation of DeGroot opinion dynamics model.
 * NO Spark dependencies - this is a standalone, testable function.
 *
 * Algorithm:
 * 1. Synchronous update: each node's new opinion = average of its in-neighbors' current opinions
 * 2. Convergence check: max_i |opinion_i(t+1) - opinion_i(t)| < epsilon
 * 3. Hard cap on iterations
 * 4. Nodes with zero in-neighbors never change (handled correctly)
 */
public class DeGrootSimulator {

    private static final double DEFAULT_EPSILON = 0.01;
    private static final int DEFAULT_MAX_ROUNDS = 500;

    /**
     * Result of a DeGroot simulation
     */
    public static class SimulationResult implements Serializable {
        private final boolean converged;
        private final int rounds;
        private final Map<Long, Double> finalOpinions;
        private final double finalMean;
        private final double finalVariance;

        public SimulationResult(boolean converged, int rounds, Map<Long, Double> finalOpinions,
                                double finalMean, double finalVariance) {
            this.converged = converged;
            this.rounds = rounds;
            this.finalOpinions = finalOpinions;
            this.finalMean = finalMean;
            this.finalVariance = finalVariance;
        }

        public boolean isConverged() {
            return converged;
        }

        public int getRounds() {
            return rounds;
        }

        public Map<Long, Double> getFinalOpinions() {
            return finalOpinions;
        }

        public double getFinalMean() {
            return finalMean;
        }

        public double getFinalVariance() {
            return finalVariance;
        }
    }

    /**
     * Run DeGroot simulation with default parameters.
     *
     * @param graph The graph structure (with in-neighbors adjacency list)
     * @param initialOpinions Initial opinion values for each node
     * @return SimulationResult with convergence status and statistics
     */
    public static SimulationResult runSimulation(
            GraphLoader.Graph graph,
            Map<Long, Double> initialOpinions) {
        return runSimulation(graph, initialOpinions, DEFAULT_EPSILON, DEFAULT_MAX_ROUNDS, 1.0);
    }

    /**
     * Run DeGroot simulation with custom parameters.
     *
     * @param graph The graph structure
     * @param initialOpinions Initial opinion values
     * @param epsilon Convergence threshold
     * @param maxRounds Maximum number of iterations
     * @param alpha Inertia parameter (1.0 = no inertia, pure neighbor average)
     * @return SimulationResult with convergence status and statistics
     */
    public static SimulationResult runSimulation(
            GraphLoader.Graph graph,
            Map<Long, Double> initialOpinions,
            double epsilon,
            int maxRounds,
            double alpha) {

        Map<Long, Double> currentOpinions = new HashMap<>(initialOpinions);
        Map<Long, Double> nextOpinions = new HashMap<>();

        boolean converged = false;
        int round;

        for (round = 0; round < maxRounds; round++) {
            for (Long node : graph.getAllNodes()) {
                double newOpinion = computeNextOpinion(node, graph, currentOpinions, alpha);
                nextOpinions.put(node, newOpinion);
            }

            double maxChange = 0.0;
            for (Long node : graph.getAllNodes()) {
                double change = Math.abs(nextOpinions.get(node) - currentOpinions.get(node));
                maxChange = Math.max(maxChange, change);
            }

            Map<Long, Double> temp = currentOpinions;
            currentOpinions = nextOpinions;
            nextOpinions = temp;
            nextOpinions.clear();

            if (maxChange < epsilon) {
                converged = true;
                round++;
                break;
            }
        }

        double mean = computeMean(currentOpinions);
        double variance = computeVariance(currentOpinions, mean);

        return new SimulationResult(converged, round, currentOpinions, mean, variance);
    }

    /**
     * Compute next opinion for a single node.
     *
     * @param node The node to update
     * @param graph Graph structure
     * @param currentOpinions Current opinion values
     * @param alpha Inertia parameter (currently unused, but prepared for future)
     * @return New opinion value
     */
    private static double computeNextOpinion(
            Long node,
            GraphLoader.Graph graph,
            Map<Long, Double> currentOpinions,
            double alpha) {

        List<Long> inNeighbors = graph.getInNeighbors().get(node);

        if (inNeighbors == null || inNeighbors.isEmpty()) {
            return currentOpinions.get(node);
        }

        double sum = 0.0;
        for (Long neighbor : inNeighbors) {
            sum += currentOpinions.get(neighbor);
        }
        double neighborAvg = sum / inNeighbors.size();

        return neighborAvg;
    }

    /**
     * Compute mean of opinion values
     */
    private static double computeMean(Map<Long, Double> opinions) {
        if (opinions.isEmpty()) {
            return 0.0;
        }

        double sum = 0.0;
        for (Double opinion : opinions.values()) {
            sum += opinion;
        }
        return sum / opinions.size();
    }

    /**
     * Compute variance of opinion values
     */
    private static double computeVariance(Map<Long, Double> opinions, double mean) {
        if (opinions.isEmpty()) {
            return 0.0;
        }

        double sumSquaredDiff = 0.0;
        for (Double opinion : opinions.values()) {
            double diff = opinion - mean;
            sumSquaredDiff += diff * diff;
        }
        return sumSquaredDiff / opinions.size();
    }
}
