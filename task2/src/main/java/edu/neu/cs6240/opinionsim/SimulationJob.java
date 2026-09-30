package edu.neu.cs6240.opinionsim;

import java.io.Serializable;

/**
 * Represents a single simulation job to be executed.
 * Each job is a combination of (graph_variant, seeding_strategy, trial_number).
 */
public class SimulationJob implements Serializable {
    private final String graphFilePath;
    private final String graphVariantName;
    private final OpinionInitializer.SeedingStrategy seedingStrategy;
    private final int trialNumber;
    private final long randomSeed;

    public SimulationJob(String graphFilePath,
                         String graphVariantName,
                         OpinionInitializer.SeedingStrategy seedingStrategy,
                         int trialNumber,
                         long randomSeed) {
        this.graphFilePath = graphFilePath;
        this.graphVariantName = graphVariantName;
        this.seedingStrategy = seedingStrategy;
        this.trialNumber = trialNumber;
        this.randomSeed = randomSeed;
    }

    public String getGraphFilePath() {
        return graphFilePath;
    }

    public String getGraphVariantName() {
        return graphVariantName;
    }

    public OpinionInitializer.SeedingStrategy getSeedingStrategy() {
        return seedingStrategy;
    }

    public int getTrialNumber() {
        return trialNumber;
    }

    public long getRandomSeed() {
        return randomSeed;
    }

    @Override
    public String toString() {
        return String.format("Job[%s, %s, trial=%d, seed=%d]",
                graphVariantName, seedingStrategy, trialNumber, randomSeed);
    }
}
