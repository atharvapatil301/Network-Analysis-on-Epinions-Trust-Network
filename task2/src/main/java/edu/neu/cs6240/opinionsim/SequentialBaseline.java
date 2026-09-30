package edu.neu.cs6240.opinionsim;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.*;

/**
 * Sequential baseline for opinion simulation.
 * NO Spark, NO parallelism - just a plain for-loop running all simulations sequentially.
 *
 * This is the control version to compare against the distributed Spark job for:
 * 1. Correctness verification (results should match)
 * 2. Speedup measurement (total wall-clock time comparison)
 *
 * IMPORTANT: Reuses the same DeGroot simulation function as the Spark version,
 * so both are guaranteed to produce identical results.
 */
public class SequentialBaseline {

    // Trial configuration (same as Spark version)
    private static final int DETERMINISTIC_GRAPH_RANDOMIZED_SEEDING_TRIALS = 10;
    private static final int RANDOM_GRAPH_SEEDING_TRIALS = 5;

    public static void main(String[] args) {
        // Parse command line arguments
        if (args.length < 2) {
            System.err.println("Usage: SequentialBaseline <input-directory> <output-csv-path>");
            System.err.println("  input-directory: Directory containing graph variant .txt files");
            System.err.println("  output-csv-path: Path to output CSV file");
            System.exit(1);
        }

        String inputDirectory = args[0];
        String outputCsvPath = args[1];

        System.out.println("=== Sequential Baseline - Opinion Simulation ===");
        System.out.println("Input directory: " + inputDirectory);
        System.out.println("Output CSV: " + outputCsvPath);

        long startTime = System.currentTimeMillis();

        try {
            // Step 1: Get all graph variant files
            List<String> graphFiles = GraphLoader.getGraphVariantFiles(inputDirectory);
            System.out.println("Found " + graphFiles.size() + " graph variant files");

            // Step 2: Load all graphs into memory
            Map<String, GraphLoader.Graph> graphs = new HashMap<>();

            for (String graphFile : graphFiles) {
                String variantName = GraphLoader.getGraphVariantName(graphFile);
                System.out.println("Loading graph: " + variantName + " from " + graphFile);

                GraphLoader.Graph graph = GraphLoader.loadGraph(graphFile);
                System.out.println("  Loaded " + graph.getNodeCount() + " nodes, " +
                        graph.getEdgeCount() + " edges");

                graphs.put(variantName, graph);
            }

            // Step 3: Build jobs list
            List<SimulationJob> jobs = buildJobsList(graphFiles);
            System.out.println("Created " + jobs.size() + " total jobs");

            // Step 4: Run all simulations SEQUENTIALLY
            List<OutputRow> results = new ArrayList<>();
            int jobIndex = 0;

            for (SimulationJob job : jobs) {
                jobIndex++;
                if (jobIndex % 10 == 0) {
                    System.out.println("Progress: " + jobIndex + "/" + jobs.size() + " jobs completed");
                }

                // Get the graph for this job
                GraphLoader.Graph graph = graphs.get(job.getGraphVariantName());

                // Initialize opinions
                Map<Long, Double> initialOpinions = OpinionInitializer.initializeOpinions(
                        graph,
                        job.getSeedingStrategy(),
                        job.getRandomSeed()
                );

                // Run DeGroot simulation (SAME function as Spark version)
                DeGrootSimulator.SimulationResult simResult = DeGrootSimulator.runSimulation(
                        graph,
                        initialOpinions
                );

                // Create output row
                OutputRow result = new OutputRow(
                        job.getGraphVariantName(),
                        job.getSeedingStrategy().name(),
                        job.getTrialNumber(),
                        simResult.isConverged(),
                        simResult.getRounds(),
                        simResult.getFinalVariance(),
                        simResult.getFinalMean()
                );

                results.add(result);
            }

            // Step 5: Write results to CSV
            writeResultsToCSV(results, outputCsvPath);
            System.out.println("Results written to " + outputCsvPath);

            long endTime = System.currentTimeMillis();
            double totalTimeSeconds = (endTime - startTime) / 1000.0;

            System.out.println("=== Sequential Baseline Complete ===");
            System.out.println("Total jobs executed: " + results.size());
            System.out.println("Total wall-clock time: " + String.format("%.2f", totalTimeSeconds) + " seconds");
            System.out.println("Average time per job: " +
                    String.format("%.3f", totalTimeSeconds / results.size()) + " seconds");

        } catch (Exception e) {
            System.err.println("Error during sequential simulation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Build the full list of simulation jobs (same logic as Spark version).
     */
    private static List<SimulationJob> buildJobsList(List<String> graphFiles) {
        List<SimulationJob> jobs = new ArrayList<>();
        Random seedGenerator = new Random(42);

        for (String graphFile : graphFiles) {
            String variantName = GraphLoader.getGraphVariantName(graphFile);
            boolean isRandomRemoved = variantName.contains("random_removed");

            int numTrials = isRandomRemoved ? RANDOM_GRAPH_SEEDING_TRIALS : DETERMINISTIC_GRAPH_RANDOMIZED_SEEDING_TRIALS;

            for (OpinionInitializer.SeedingStrategy strategy :
                    Arrays.asList(OpinionInitializer.SeedingStrategy.UNIFORM_RANDOM,
                            OpinionInitializer.SeedingStrategy.BIMODAL_COMMUNITY)) {

                for (int trial = 0; trial < numTrials; trial++) {
                    long seed = seedGenerator.nextLong();
                    jobs.add(new SimulationJob(graphFile, variantName, strategy, trial, seed));
                }
            }

            jobs.add(new SimulationJob(
                    graphFile,
                    variantName,
                    OpinionInitializer.SeedingStrategy.SINGLE_SEED_HIGHDEGREE,
                    0,
                    0
            ));
        }

        return jobs;
    }

    /**
     * Write results to CSV file (same format as Spark version).
     * Supports both local filesystem and S3 paths.
     */
    private static void writeResultsToCSV(List<OutputRow> results, String outputPath) throws IOException {
        org.apache.hadoop.conf.Configuration conf = new org.apache.hadoop.conf.Configuration();
        org.apache.hadoop.fs.Path path = new org.apache.hadoop.fs.Path(outputPath);
        org.apache.hadoop.fs.FileSystem fs = path.getFileSystem(conf);

        try (BufferedWriter writer = new BufferedWriter(
                new java.io.OutputStreamWriter(fs.create(path, true)))) {
            writer.write(OutputRow.getCSVHeader());
            writer.newLine();

            for (OutputRow row : results) {
                writer.write(row.toCSV());
                writer.newLine();
            }
        }
    }
}
