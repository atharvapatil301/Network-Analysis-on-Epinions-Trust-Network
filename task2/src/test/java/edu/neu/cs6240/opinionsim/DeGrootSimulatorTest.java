package edu.neu.cs6240.opinionsim;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.*;

/**
 * Standalone test for DeGroot simulator.
 * Verifies convergence to sensible consensus values on small toy graphs.
 */
public class DeGrootSimulatorTest {

    /**
     * Test 1: Simple chain graph (1→2→3)
     * Node 1 trusts 2, node 2 trusts 3, node 3 has no out-edges.
     * Expected: opinions should converge toward node 3's initial opinion.
     */
    @Test
    public void testSimpleChain() {
        // Create graph: 1→2, 2→3
        // This means: node 1's opinion is influenced by node 2
        //             node 2's opinion is influenced by node 3
        //             node 3 has no in-neighbors (opinion stays constant)

        Map<Long, List<Long>> inNeighbors = new HashMap<>();
        inNeighbors.put(1L, Arrays.asList(2L));  // 1 trusts 2
        inNeighbors.put(2L, Arrays.asList(3L));  // 2 trusts 3
        inNeighbors.put(3L, new ArrayList<>());  // 3 trusts nobody (isolated sink)

        Map<Long, Integer> inDegree = new HashMap<>();
        inDegree.put(1L, 0);
        inDegree.put(2L, 1);
        inDegree.put(3L, 1);

        Map<Long, Integer> outDegree = new HashMap<>();
        outDegree.put(1L, 1);
        outDegree.put(2L, 1);
        outDegree.put(3L, 0);

        Set<Long> allNodes = new HashSet<>(Arrays.asList(1L, 2L, 3L));

        GraphLoader.Graph graph = new GraphLoader.Graph(inNeighbors, inDegree, outDegree, allNodes);

        // Initial opinions: 1=0.0, 2=0.0, 3=1.0
        Map<Long, Double> initialOpinions = new HashMap<>();
        initialOpinions.put(1L, 0.0);
        initialOpinions.put(2L, 0.0);
        initialOpinions.put(3L, 1.0);

        // Run simulation
        DeGrootSimulator.SimulationResult result = DeGrootSimulator.runSimulation(graph, initialOpinions);

        // Verify convergence
        assertTrue("Should converge", result.isConverged());

        // Node 3 should stay at 1.0 (no in-neighbors)
        assertEquals(1.0, result.getFinalOpinions().get(3L), 0.001);

        // Node 2 should converge toward 1.0 (averages node 3)
        assertTrue("Node 2 should approach 1.0", result.getFinalOpinions().get(2L) > 0.9);

        // Node 1 should also converge toward 1.0 (averages node 2)
        assertTrue("Node 1 should approach 1.0", result.getFinalOpinions().get(1L) > 0.8);

        System.out.println("Test 1 (Chain): Converged in " + result.getRounds() + " rounds");
        System.out.println("  Final opinions: " + result.getFinalOpinions());
        System.out.println("  Mean: " + result.getFinalMean() + ", Variance: " + result.getFinalVariance());
    }

    /**
     * Test 2: Fully connected triangle with uniform initial opinions
     * All nodes trust each other, should converge to consensus at the mean.
     */
    @Test
    public void testFullyConnectedTriangle() {
        // Create graph: 1↔2, 2↔3, 1↔3 (all bidirectional)
        Map<Long, List<Long>> inNeighbors = new HashMap<>();
        inNeighbors.put(1L, Arrays.asList(2L, 3L));
        inNeighbors.put(2L, Arrays.asList(1L, 3L));
        inNeighbors.put(3L, Arrays.asList(1L, 2L));

        Map<Long, Integer> inDegree = new HashMap<>();
        inDegree.put(1L, 2);
        inDegree.put(2L, 2);
        inDegree.put(3L, 2);

        Map<Long, Integer> outDegree = new HashMap<>();
        outDegree.put(1L, 2);
        outDegree.put(2L, 2);
        outDegree.put(3L, 2);

        Set<Long> allNodes = new HashSet<>(Arrays.asList(1L, 2L, 3L));

        GraphLoader.Graph graph = new GraphLoader.Graph(inNeighbors, inDegree, outDegree, allNodes);

        // Initial opinions: 1=-1.0, 2=0.0, 3=1.0
        Map<Long, Double> initialOpinions = new HashMap<>();
        initialOpinions.put(1L, -1.0);
        initialOpinions.put(2L, 0.0);
        initialOpinions.put(3L, 1.0);

        double expectedMean = 0.0; // (-1 + 0 + 1) / 3

        // Run simulation
        DeGrootSimulator.SimulationResult result = DeGrootSimulator.runSimulation(graph, initialOpinions);

        // Verify convergence
        assertTrue("Should converge", result.isConverged());

        // All nodes should converge to the mean (consensus)
        assertEquals(expectedMean, result.getFinalOpinions().get(1L), 0.01);
        assertEquals(expectedMean, result.getFinalOpinions().get(2L), 0.01);
        assertEquals(expectedMean, result.getFinalOpinions().get(3L), 0.01);

        // Variance should be near zero (consensus reached)
        assertTrue("Variance should be near zero", result.getFinalVariance() < 0.001);

        System.out.println("Test 2 (Triangle): Converged in " + result.getRounds() + " rounds");
        System.out.println("  Final opinions: " + result.getFinalOpinions());
        System.out.println("  Mean: " + result.getFinalMean() + ", Variance: " + result.getFinalVariance());
    }

    /**
     * Test 3: Star graph with single influencer
     * One central node (high in-degree) influences many others.
     */
    @Test
    public void testStarGraph() {
        // Create star graph: 1→5, 2→5, 3→5, 4→5
        // Node 5 is the center (highest in-degree), others trust it

        Map<Long, List<Long>> inNeighbors = new HashMap<>();
        inNeighbors.put(1L, Arrays.asList(5L));
        inNeighbors.put(2L, Arrays.asList(5L));
        inNeighbors.put(3L, Arrays.asList(5L));
        inNeighbors.put(4L, Arrays.asList(5L));
        inNeighbors.put(5L, new ArrayList<>()); // Center has no in-neighbors

        Map<Long, Integer> inDegree = new HashMap<>();
        inDegree.put(1L, 0);
        inDegree.put(2L, 0);
        inDegree.put(3L, 0);
        inDegree.put(4L, 0);
        inDegree.put(5L, 4); // Highest in-degree

        Map<Long, Integer> outDegree = new HashMap<>();
        outDegree.put(1L, 1);
        outDegree.put(2L, 1);
        outDegree.put(3L, 1);
        outDegree.put(4L, 1);
        outDegree.put(5L, 0);

        Set<Long> allNodes = new HashSet<>(Arrays.asList(1L, 2L, 3L, 4L, 5L));

        GraphLoader.Graph graph = new GraphLoader.Graph(inNeighbors, inDegree, outDegree, allNodes);

        // Initial opinions: center node (5) has opinion 1.0, others start at 0.0
        Map<Long, Double> initialOpinions = new HashMap<>();
        initialOpinions.put(1L, 0.0);
        initialOpinions.put(2L, 0.0);
        initialOpinions.put(3L, 0.0);
        initialOpinions.put(4L, 0.0);
        initialOpinions.put(5L, 1.0);

        // Run simulation
        DeGrootSimulator.SimulationResult result = DeGrootSimulator.runSimulation(graph, initialOpinions);

        // Verify convergence
        assertTrue("Should converge", result.isConverged());

        // Center node (5) should stay at 1.0 (no in-neighbors)
        assertEquals(1.0, result.getFinalOpinions().get(5L), 0.001);

        // All other nodes should converge toward 1.0 (they all trust node 5)
        for (long node = 1; node <= 4; node++) {
            assertTrue("Node " + node + " should approach 1.0",
                    result.getFinalOpinions().get(node) > 0.99);
        }

        System.out.println("Test 3 (Star): Converged in " + result.getRounds() + " rounds");
        System.out.println("  Final opinions: " + result.getFinalOpinions());
        System.out.println("  Mean: " + result.getFinalMean() + ", Variance: " + result.getFinalVariance());
    }

    /**
     * Test 4: Disconnected components
     * Two separate groups should converge independently.
     */
    @Test
    public void testDisconnectedComponents() {
        // Group 1: 1↔2 (bidirectional)
        // Group 2: 3↔4 (bidirectional)
        // No connection between groups

        Map<Long, List<Long>> inNeighbors = new HashMap<>();
        inNeighbors.put(1L, Arrays.asList(2L));
        inNeighbors.put(2L, Arrays.asList(1L));
        inNeighbors.put(3L, Arrays.asList(4L));
        inNeighbors.put(4L, Arrays.asList(3L));

        Map<Long, Integer> inDegree = new HashMap<>();
        inDegree.put(1L, 1);
        inDegree.put(2L, 1);
        inDegree.put(3L, 1);
        inDegree.put(4L, 1);

        Map<Long, Integer> outDegree = new HashMap<>();
        outDegree.put(1L, 1);
        outDegree.put(2L, 1);
        outDegree.put(3L, 1);
        outDegree.put(4L, 1);

        Set<Long> allNodes = new HashSet<>(Arrays.asList(1L, 2L, 3L, 4L));

        GraphLoader.Graph graph = new GraphLoader.Graph(inNeighbors, inDegree, outDegree, allNodes);

        // Initial opinions: Group 1 at -1.0, Group 2 at +1.0
        Map<Long, Double> initialOpinions = new HashMap<>();
        initialOpinions.put(1L, -1.0);
        initialOpinions.put(2L, -1.0);
        initialOpinions.put(3L, 1.0);
        initialOpinions.put(4L, 1.0);

        // Run simulation
        DeGrootSimulator.SimulationResult result = DeGrootSimulator.runSimulation(graph, initialOpinions);

        // Verify convergence
        assertTrue("Should converge", result.isConverged());

        // Group 1 should stay near -1.0
        assertEquals(-1.0, result.getFinalOpinions().get(1L), 0.01);
        assertEquals(-1.0, result.getFinalOpinions().get(2L), 0.01);

        // Group 2 should stay near +1.0
        assertEquals(1.0, result.getFinalOpinions().get(3L), 0.01);
        assertEquals(1.0, result.getFinalOpinions().get(4L), 0.01);

        // Overall variance should be high (no consensus across groups)
        assertTrue("Variance should be high (separate groups)", result.getFinalVariance() > 0.5);

        System.out.println("Test 4 (Disconnected): Converged in " + result.getRounds() + " rounds");
        System.out.println("  Final opinions: " + result.getFinalOpinions());
        System.out.println("  Mean: " + result.getFinalMean() + ", Variance: " + result.getFinalVariance());
    }
}
