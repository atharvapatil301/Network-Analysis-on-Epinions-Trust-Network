package edu.neu.cs6240.opinionsim;

import java.io.Serializable;
import java.util.*;

/**
 * Initialize opinions for nodes using different seeding strategies.
 * All methods are pure functions that take a graph and seed, return opinion map.
 */
public class OpinionInitializer {

    public enum SeedingStrategy {
        UNIFORM_RANDOM,
        BIMODAL_COMMUNITY,
        SINGLE_SEED_HIGHDEGREE
    }

    /**
     * Initialize opinions based on the specified strategy.
     *
     * @param graph The graph structure
     * @param strategy Seeding strategy to use
     * @param seed Random seed for reproducibility
     * @return Map of nodeId -> initial opinion
     */
    public static Map<Long, Double> initializeOpinions(
            GraphLoader.Graph graph,
            SeedingStrategy strategy,
            long seed) {

        switch (strategy) {
            case UNIFORM_RANDOM:
                return uniformRandom(graph, seed);
            case BIMODAL_COMMUNITY:
                return bimodalCommunity(graph, seed);
            case SINGLE_SEED_HIGHDEGREE:
                return singleSeedHighDegree(graph);
            default:
                throw new IllegalArgumentException("Unknown seeding strategy: " + strategy);
        }
    }

    /**
     * Strategy 1: Uniform random opinions in [-1, 1]
     */
    private static Map<Long, Double> uniformRandom(GraphLoader.Graph graph, long seed) {
        Map<Long, Double> opinions = new HashMap<>();
        Random random = new Random(seed);

        for (Long node : graph.getAllNodes()) {
            double opinion = random.nextDouble() * 2.0 - 1.0;
            opinions.put(node, opinion);
        }

        return opinions;
    }

    /**
     * Strategy 2: Bimodal community based on in-degree ranking
     * Top half by in-degree: opinion ~ uniform(0.8, 1.0)
     * Bottom half by in-degree: opinion ~ uniform(-1.0, -0.8)
     */
    private static Map<Long, Double> bimodalCommunity(GraphLoader.Graph graph, long seed) {
        Map<Long, Double> opinions = new HashMap<>();
        Random random = new Random(seed);
        Map<Long, Integer> inDegree = graph.getInDegree();

        List<Long> nodesByInDegree = new ArrayList<>(graph.getAllNodes());
        nodesByInDegree.sort((a, b) -> Integer.compare(inDegree.get(b), inDegree.get(a)));

        int halfPoint = nodesByInDegree.size() / 2;

        for (int i = 0; i < nodesByInDegree.size(); i++) {
            Long node = nodesByInDegree.get(i);
            double opinion;

            if (i < halfPoint) {
                opinion = 0.8 + random.nextDouble() * 0.2;
            } else {
                opinion = -1.0 + random.nextDouble() * 0.2;
            }

            opinions.put(node, opinion);
        }

        return opinions;
    }

    /**
     * Strategy 3: Single seed at highest in-degree node
     * The node with highest in-degree (most trusted) gets opinion 1.0,
     * all others get 0.0.
     * This is deterministic (no randomness), so seed parameter is ignored.
     */
    private static Map<Long, Double> singleSeedHighDegree(GraphLoader.Graph graph) {
        Map<Long, Double> opinions = new HashMap<>();
        Map<Long, Integer> inDegree = graph.getInDegree();

        long maxInDegreeNode = -1;
        int maxInDegree = -1;

        for (Map.Entry<Long, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() > maxInDegree) {
                maxInDegree = entry.getValue();
                maxInDegreeNode = entry.getKey();
            }
        }

        for (Long node : graph.getAllNodes()) {
            if (node.equals(maxInDegreeNode)) {
                opinions.put(node, 1.0);
            } else {
                opinions.put(node, 0.0);
            }
        }

        return opinions;
    }

    /**
     * Helper class to represent a seeding job configuration
     */
    public static class SeedingJob implements Serializable {
        private final SeedingStrategy strategy;
        private final long seed;

        public SeedingJob(SeedingStrategy strategy, long seed) {
            this.strategy = strategy;
            this.seed = seed;
        }

        public SeedingStrategy getStrategy() {
            return strategy;
        }

        public long getSeed() {
            return seed;
        }
    }
}
