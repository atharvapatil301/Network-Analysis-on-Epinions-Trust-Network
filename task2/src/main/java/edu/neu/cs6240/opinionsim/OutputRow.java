package edu.neu.cs6240.opinionsim;

import java.io.Serializable;

/**
 * Represents a single output row with simulation results.
 * This will be written to CSV.
 */
public class OutputRow implements Serializable {
    private final String graphVariant;
    private final String seedingStrategy;
    private final int trialNumber;
    private final boolean converged;
    private final int rounds;
    private final double finalVariance;
    private final double finalMean;

    public OutputRow(String graphVariant,
                     String seedingStrategy,
                     int trialNumber,
                     boolean converged,
                     int rounds,
                     double finalVariance,
                     double finalMean) {
        this.graphVariant = graphVariant;
        this.seedingStrategy = seedingStrategy;
        this.trialNumber = trialNumber;
        this.converged = converged;
        this.rounds = rounds;
        this.finalVariance = finalVariance;
        this.finalMean = finalMean;
    }

    public String getGraphVariant() {
        return graphVariant;
    }

    public String getSeedingStrategy() {
        return seedingStrategy;
    }

    public int getTrialNumber() {
        return trialNumber;
    }

    public boolean isConverged() {
        return converged;
    }

    public int getRounds() {
        return rounds;
    }

    public double getFinalVariance() {
        return finalVariance;
    }

    public double getFinalMean() {
        return finalMean;
    }

    /**
     * Convert to CSV row (with header)
     */
    public static String getCSVHeader() {
        return "graph_variant,seeding_strategy,trial_number,converged,rounds,final_variance,final_mean";
    }

    /**
     * Convert to CSV row
     */
    public String toCSV() {
        return String.format("%s,%s,%d,%s,%d,%.6f,%.6f",
                graphVariant,
                seedingStrategy,
                trialNumber,
                converged,
                rounds,
                finalVariance,
                finalMean);
    }

    @Override
    public String toString() {
        return toCSV();
    }
}
