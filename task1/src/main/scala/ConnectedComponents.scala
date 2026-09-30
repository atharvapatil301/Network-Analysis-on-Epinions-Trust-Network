import org.apache.spark.sql.SparkSession
import org.apache.spark.rdd.RDD
import org.apache.hadoop.fs.{FileSystem, Path}
import java.net.URI

/**
 * ConnectedComponents
 *
 * Task 1 (ban-wave side), stage 2: computes connected-component
 * statistics for a given graph variant (original, degree-removed,
 * community-removed, or one of the random-removed trials), so we can
 * measure how much each removal strategy fragments the network.
 *
 * Algorithm: iterative min-label propagation (a standard distributed
 * connected-components algorithm). Each node starts labeled with its own
 * ID. Every round, each node adopts the minimum label among itself and
 * its neighbors (map: emit each node's label to its neighbors; shuffle:
 * group by receiving node; reduce: take the min). Because the minimum
 * label always eventually reaches every node in the same component,
 * this converges to one distinct label per connected component.
 *
 * Usage:
 *   spark-submit --class ConnectedComponents <jar> <input_edges_path> <output_stats_path> [max_iterations]
 *
 * Input: any of the tab-separated edge-list files produced by
 * GraphVariantGenerator (from_node \t to_node per line, no header).
 *
 * Output (single line, written to output_stats_path):
 *   total_nodes,num_components,largest_component_size,largest_component_fraction,iterations_run
 */
object ConnectedComponents {

  val DEFAULT_MAX_ITERATIONS = 50
  val CHECKPOINT_EVERY = 4 // truncate RDD lineage every N rounds
  val NUM_PARTITIONS = 32 // fixed partition count for the per-round reduceByKey

  def main(args: Array[String]): Unit = {
    if (args.length < 2) {
      println("Usage: ConnectedComponents <input_edges_path> <output_stats_path> [max_iterations] [checkpoint_dir]")
      System.exit(1)
    }
    val inputPath = args(0)
    val outputStatsPath = args(1)
    val maxIterations = if (args.length > 2) args(2).toInt else DEFAULT_MAX_ITERATIONS
    val checkpointDir = if (args.length > 3) args(3) else "/tmp/cc-checkpoint"

    val spark = SparkSession.builder().appName("ConnectedComponents").getOrCreate()
    val sc = spark.sparkContext
    sc.setCheckpointDir(checkpointDir)

    val rawEdges: RDD[(Long, Long)] = sc.textFile(inputPath)
      .filter(line => !line.startsWith("#") && line.trim.nonEmpty)
      .map { line =>
        val parts = line.split("\\s+")
        (parts(0).toLong, parts(1).toLong)
      }

    // Connectivity is direction-agnostic here: add both directions so a
    // node can receive labels from and send labels to every neighbor,
    // regardless of the original trust edge's direction.
    val undirected = rawEdges.flatMap { case (f, t) => Seq((f, t), (t, f)) }.distinct().cache()

    val allNodes = undirected.map(_._1).distinct().cache()
    val totalNodes = allNodes.count()

    var labels: RDD[(Long, Long)] = allNodes.map(n => (n, n)).cache()
    var iterationsRun = 0
    var changed = true

    while (changed && iterationsRun < maxIterations) {
      // Map: join undirected edges (u, v) with labels keyed by u to fetch
      // u's current label, then re-key by v -- this is what makes v the
      // *receiver* of u's label as a candidate this round.
      val neighborCandidates = undirected
        .join(labels) // (u, (v, labelOfU))
        .map { case (_, (v, labelOfU)) => (v, labelOfU) }

      // Every node also proposes its own current label as a candidate,
      // so a node that is already the minimum in its component keeps
      // that label instead of losing it to a neighbor's stale value.
      val selfCandidates = labels

      // Reduce: take the minimum label proposed for each node. This is
      // the actual shuffle-and-reduce step -- one round of
      // groupByKey-then-min, structurally identical to a PageRank or
      // BFS iteration.
      // Explicitly fix the partition count here. The "++" (union) above
      // concatenates partitions from both sides rather than merging them,
      // so without pinning numPartitions, partition count roughly doubles
      // every iteration -- after ~10 rounds this balloons into thousands
      // of tiny partitions, each needing its own shuffle file, which is
      // what actually caused the "no space left on device" failures
      // (file/metadata overhead, not real data volume).
      val newLabels = (neighborCandidates ++ selfCandidates)
        .reduceByKey((a: Long, b: Long) => math.min(a, b), NUM_PARTITIONS)
        .cache()

      // Every few rounds, checkpoint to truncate the RDD's lineage graph.
      // Without this, each round's plan includes every prior round's
      // joins/reduces, so re-evaluation cost (and driver-side DAG
      // bookkeeping) grows with iteration count even though the data is
      // cached -- a well-known pitfall for iterative Spark jobs (same
      // issue iterative PageRank hits without checkpointing).
      if (iterationsRun % CHECKPOINT_EVERY == 0) {
        newLabels.checkpoint()
        newLabels.count() // force materialization so the checkpoint actually happens now
      }

      // Convergence check: count how many nodes changed label this round.
      val numChanged = labels.join(newLabels)
        .filter { case (_, (oldLabel, newLabel)) => oldLabel != newLabel }
        .count()

      labels.unpersist()
      labels = newLabels
      iterationsRun += 1
      changed = numChanged > 0
    }

    val componentSizes = labels.map { case (_, label) => (label, 1L) }.reduceByKey(_ + _)
    val numComponents = componentSizes.count()
    val largestComponentSize = componentSizes.map(_._2).max()
    val largestComponentFraction = largestComponentSize.toDouble / totalNodes.toDouble

    val statsLine =
      f"$totalNodes,$numComponents,$largestComponentSize,$largestComponentFraction%.4f,$iterationsRun"

    // Clean up any leftover output from a prior run on this same path
    // (e.g. a smaller-cluster speedup comparison run) so this job is
    // idempotent -- otherwise saveAsTextFile refuses to write into a
    // path that already exists.
    val outputPathObj = new Path(outputStatsPath)
    val fs = FileSystem.get(new URI(outputStatsPath), sc.hadoopConfiguration)
    if (fs.exists(outputPathObj)) fs.delete(outputPathObj, true)

    sc.parallelize(Seq(statsLine), 1).saveAsTextFile(outputStatsPath)

    spark.stop()
  }
}