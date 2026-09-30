import org.apache.spark.sql.SparkSession
import org.apache.spark.rdd.RDD
import org.apache.hadoop.fs.{FileSystem, Path}
import java.net.URI
import scala.util.Random

/**
 * GraphVariantGenerator
 *
 * Task 1 (ban-wave side): produces a fixed set of graph variant files that
 * feed directly into the DeGroot opinion-dynamics task:
 *
 *   original.txt
 *   degree_removed_10pct.txt
 *   community_removed_10pct.txt
 *   random_removed_10pct_trial1.txt ... trial{K}.txt
 *
 * Each file is a plain tab-separated edge list (from_node \t to_node),
 * with the same schema as the original soc-Epinions1 input so the
 * downstream DeGroot job can read any of them without special-casing.
 *
 * Usage:
 *   spark-submit --class GraphVariantGenerator <jar> <input_path> <output_dir> <k_random_trials>
 */
object GraphVariantGenerator {

  val REMOVAL_PCT = 0.10
  val LPA_MAX_ITERATIONS = 10

  def main(args: Array[String]): Unit = {
    if (args.length != 3) {
      println("Usage: GraphVariantGenerator <input_path> <output_dir> <k_random_trials>")
      System.exit(1)
    }
    val inputPath = args(0)
    val outputDir = args(1)
    val kTrials = args(2).toInt

    val spark = SparkSession.builder().appName("GraphVariantGenerator").getOrCreate()
    val sc = spark.sparkContext

    // Load raw edges, skipping SNAP's '#' comment header lines.
    val rawEdges: RDD[(Long, Long)] = sc.textFile(inputPath)
      .filter(line => !line.startsWith("#") && line.trim.nonEmpty)
      .map { line =>
        val parts = line.split("\\s+")
        (parts(0).toLong, parts(1).toLong)
      }
      .cache()

    val allNodes = rawEdges.flatMap { case (f, t) => Seq(f, t) }.distinct().cache()
    val totalNodes = allNodes.count()
    val numToRemove = math.round(totalNodes * REMOVAL_PCT).toInt

    // ---------- Variant: original (pass-through, cleaned of comments) ----------
    writeEdges(rawEdges, s"$outputDir/original", spark)

    // ---------- Variant: degree-removed (top 10% by total degree) ----------
    val outDeg = rawEdges.map { case (f, _) => (f, 1) }.reduceByKey(_ + _)
    val inDeg = rawEdges.map { case (_, t) => (t, 1) }.reduceByKey(_ + _)
    val totalDeg = outDeg.fullOuterJoin(inDeg)
      .map { case (node, (outO, inO)) => (node, outO.getOrElse(0) + inO.getOrElse(0)) }

    val degreeRemovedNodes = totalDeg
      .sortBy(_._2, ascending = false)
      .take(numToRemove)
      .map(_._1)
      .toSet
    val degreeRemovedBc = sc.broadcast(degreeRemovedNodes)

    val degreeRemovedEdges = rawEdges.filter { case (f, t) =>
      !degreeRemovedBc.value.contains(f) && !degreeRemovedBc.value.contains(t)
    }
    writeEdges(degreeRemovedEdges, s"$outputDir/degree_removed_10pct", spark)
    degreeRemovedBc.destroy()

    // ---------- Variant: community-removed (label propagation) ----------
    val communityLabels = labelPropagation(rawEdges, allNodes, LPA_MAX_ITERATIONS)
    val communitySizes = communityLabels.map { case (_, label) => (label, 1) }.reduceByKey(_ + _)

    // Greedily accumulate whole communities (smallest first) until reaching
    // ~10% of all nodes, so the removed set is a union of intact
    // communities rather than an arbitrary node cut.
    val sortedCommunities = communitySizes.collect().sortBy(_._2)
    var accumulated = 0L
    val communitiesToRemove = scala.collection.mutable.Set[Long]()
    for ((label, size) <- sortedCommunities if accumulated < numToRemove) {
      communitiesToRemove += label
      accumulated += size
    }
    val communitiesToRemoveBc = sc.broadcast(communitiesToRemove.toSet)
    val nodesToRemoveCommunity = communityLabels
      .filter { case (_, label) => communitiesToRemoveBc.value.contains(label) }
      .map(_._1)
      .collect()
      .toSet
    communitiesToRemoveBc.destroy()
    val nodesToRemoveCommunityBc = sc.broadcast(nodesToRemoveCommunity)

    val communityRemovedEdges = rawEdges.filter { case (f, t) =>
      !nodesToRemoveCommunityBc.value.contains(f) && !nodesToRemoveCommunityBc.value.contains(t)
    }
    writeEdges(communityRemovedEdges, s"$outputDir/community_removed_10pct", spark)
    nodesToRemoveCommunityBc.destroy()

    // ---------- Variant: random-removed, K trials ----------
    val nodeList = allNodes.collect()
    for (trial <- 1 to kTrials) {
      // Fixed seed per trial number (not just System time) so the exact
      // same K graph files can be regenerated later if needed, e.g. to
      // re-run a failed EMR step without changing the experiment.
      val rnd = new Random(trial)
      val shuffled = rnd.shuffle(nodeList.toList)
      val removedSet = shuffled.take(numToRemove).toSet
      val removedSetBc = sc.broadcast(removedSet)

      val randomRemovedEdges = rawEdges.filter { case (f, t) =>
        !removedSetBc.value.contains(f) && !removedSetBc.value.contains(t)
      }
      writeEdges(randomRemovedEdges, s"$outputDir/random_removed_10pct_trial$trial", spark)
      removedSetBc.destroy()
    }

    spark.stop()
  }

  /** Writes an edge RDD as a single tab-separated text file at
   * `<path>.txt` (merges Spark's multi-part output into one file, since
   * the DeGroot job expects a plain graph file, not a part-file
   * directory). */
  def writeEdges(edges: RDD[(Long, Long)], path: String, spark: SparkSession): Unit = {
    val tmpPath = path + "_tmp"
    val finalPath = path + ".txt"

    // Resolve the filesystem from the path's own URI/scheme (e.g. "s3://"),
    // rather than the cluster's default filesystem (HDFS on EMR) -- using
    // the wrong filesystem handle here would silently target the wrong
    // storage layer when running on a cluster.
    val conf = spark.sparkContext.hadoopConfiguration
    val fs = FileSystem.get(new URI(tmpPath), conf)

    val tmpPathObj = new Path(tmpPath)
    val finalPathObj = new Path(finalPath)

    // Clean up any leftover output from a prior attempt (a manual re-run,
    // or YARN automatically retrying a failed Spark application) so this
    // job is idempotent -- otherwise saveAsTextFile refuses to write into
    // a directory that already exists.
    if (fs.exists(tmpPathObj)) fs.delete(tmpPathObj, true)
    if (fs.exists(finalPathObj)) fs.delete(finalPathObj, true)

    edges.map { case (f, t) => s"$f\t$t" }.coalesce(1).saveAsTextFile(tmpPath)

    val partFile = fs.listStatus(tmpPathObj)
      .map(_.getPath)
      .find(_.getName.startsWith("part-"))
      .get
    fs.rename(partFile, finalPathObj)
    fs.delete(tmpPathObj, true)
  }

  /** Synchronous label propagation for community detection: each node
   * adopts the most frequent label among its neighbors (ties broken by
   * smallest label id, for determinism across runs), repeated for a
   * fixed number of rounds. This uses the same iterative map/reduce
   * pattern as PageRank -- one shuffle round per iteration.
   *
   * The graph is treated as undirected for this step: a directed trust
   * edge A->B still indicates A and B are "close" for clustering
   * purposes, so both directions are added before propagating labels. */
  def labelPropagation(
                        edges: RDD[(Long, Long)],
                        nodes: RDD[Long],
                        maxIterations: Int
                      ): RDD[(Long, Long)] = {
    val undirected = edges.flatMap { case (f, t) => Seq((f, t), (t, f)) }.distinct()

    var labels: RDD[(Long, Long)] = nodes.map(n => (n, n)) // each node starts as its own label

    for (_ <- 1 to maxIterations) {
      // Key edges by neighbor so we can join with that neighbor's current
      // label, producing (node, neighborLabel) pairs for every edge.
      val neighborLabelPairs = undirected
        .map { case (node, neighbor) => (neighbor, node) }
        .join(labels)
        .map { case (_, (node, neighborLabel)) => (node, neighborLabel) }

      labels = neighborLabelPairs
        .groupByKey()
        .map { case (node, neighborLabels) =>
          val best = neighborLabels
            .groupBy(identity)
            .map { case (label, occurrences) => (label, occurrences.size) }
            .toSeq
            .sortBy { case (label, count) => (-count, label) } // most frequent; tie -> smallest label id
            .head
            ._1
          (node, best)
        }
    }
    labels
  }
}