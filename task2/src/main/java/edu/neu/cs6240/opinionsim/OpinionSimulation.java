package edu.neu.cs6240.opinionsim;

import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.broadcast.Broadcast;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.*;

/**
 * Main Spark driver for Opinion Simulation using DeGroot Model.
 *
 * Architecture:
 * 1. Load each graph variant into memory (plain Java HashMap)
 * 2. Broadcast graph structures to all executors
 * 3. Create RDD of jobs (graph_variant x seeding_strategy x trial)
 * 4. Use map() to run local DeGroot simulations (no Spark operations inside)
 * 5. Collect results and write to CSV
 *
 * NO shuffles, NO joins - just broadcast + embarrassingly parallel map operations.
 */
public class OpinionSimulation {
    private static final Logger logger = LoggerFactory.getLogger(OpinionSimulation.class);

    // Trial configuration
    private static final int DETERMINISTIC_GRAPH_RANDOMIZED_SEEDING_TRIALS = 10;
    private static final int RANDOM_GRAPH_SEEDING_TRIALS = 5;
    private static final int SINGLE_SEED_TRIALS = 1;  // Deterministic strategy

    public static void main(String[] args) {
        // Parse command line arguments
        if (args.length < 2) {
            System.err.println("Usage: OpinionSimulation <input-directory> <output-csv-path>");
            System.err.println("  input-directory: Directory containing graph variant .txt files");
            System.err.println("  output-csv-path: Path to output CSV file");
            System.exit(1);
        }

        String inputDirectory = args[0];
        String outputCsvPath = args[1];

        logger.info("=== Opinion Simulation with DeGroot Model ===");
        logger.info("Input directory: {}", inputDirectory);
        logger.info("Output CSV: {}", outputCsvPath);

        // Create Spark context
        SparkConf conf = new SparkConf()
                .setAppName("OpinionSimulation-DeGroot")
                .set("spark.serializer", "org.apache.spark.serializer.KryoSerializer");

        JavaSparkContext jsc = new JavaSparkContext(conf);

        try {
            // Step 1: Get all graph variant files
            List<String> graphFiles = GraphLoader.getGraphVariantFiles(inputDirectory);
            logger.info("Found {} graph variant files", graphFiles.size());

            // Step 2: Load graphs and create broadcast variables
            Map<String, Broadcast<GraphLoader.Graph>> broadcastedGraphs = new HashMap<>();

            for (String graphFile : graphFiles) {
                String variantName = GraphLoader.getGraphVariantName(graphFile);
                logger.info("Loading graph: {} from {}", variantName, graphFile);

                GraphLoader.Graph graph = GraphLoader.loadGraph(graphFile);
                logger.info("  Loaded {} nodes, {} edges", graph.getNodeCount(), graph.getEdgeCount());

                // Broadcast this graph to all executors
                Broadcast<GraphLoader.Graph> broadcastGraph = jsc.broadcast(graph);
                broadcastedGraphs.put(variantName, broadcastGraph);
            }

            // Step 3: Build jobs list
            List<SimulationJob> jobs = buildJobsList(graphFiles);
            logger.info("Created {} total jobs", jobs.size());

            JavaRDD<SimulationJob> jobsRDD = jsc.parallelize(jobs);

            JavaRDD<OutputRow> results = jobsRDD.map(job -> {
                System.out.println("Running job: " + job);

                Broadcast<GraphLoader.Graph> broadcastGraph = broadcastedGraphs.get(job.getGraphVariantName());
                GraphLoader.Graph graph = broadcastGraph.value();

                Map<Long, Double> initialOpinions = OpinionInitializer.initializeOpinions(
                        graph,
                        job.getSeedingStrategy(),
                        job.getRandomSeed()
                );

                DeGrootSimulator.SimulationResult simResult = DeGrootSimulator.runSimulation(
                        graph,
                        initialOpinions
                );

                return new OutputRow(
                        job.getGraphVariantName(),
                        job.getSeedingStrategy().name(),
                        job.getTrialNumber(),
                        simResult.isConverged(),
                        simResult.getRounds(),
                        simResult.getFinalVariance(),
                        simResult.getFinalMean()
                );
            });

            List<OutputRow> resultsList = results.collect();
            logger.info("Collected {} results", resultsList.size());

            writeResultsToCSV(resultsList, outputCsvPath);
            logger.info("Results written to {}", outputCsvPath);

            for (Broadcast<GraphLoader.Graph> broadcast : broadcastedGraphs.values()) {
                broadcast.unpersist();
            }

        } catch (Exception e) {
            logger.error("Error during simulation", e);
            throw new RuntimeException(e);
        } finally {
            jsc.close();
        }

        logger.info("=== Opinion Simulation Complete ===");
    }

    /**
     * Build the full list of simulation jobs.
     *
     * Trial configuration:
     * - Deterministic graphs (original, degree_removed, community_removed) with randomized seeding:
     *   10 trials each
     * - Random_removed graphs with randomized seeding: 5 trials each (already have K=5 graph variants)
     * - Single_seed_highdegree is deterministic: 1 trial per graph variant
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
     * Write results to CSV file.
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
