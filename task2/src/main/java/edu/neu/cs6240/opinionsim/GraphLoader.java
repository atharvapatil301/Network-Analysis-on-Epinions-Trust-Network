package edu.neu.cs6240.opinionsim;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.Serializable;
import java.util.*;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;

/**
 * Loads graph from edge list file and builds in-memory adjacency structure.
 * This is NOT a Spark-based loader - it builds plain Java data structures.
 */
public class GraphLoader {

    /**
     * Represents a directed graph with in-neighbor adjacency lists.
     * Edge direction: 1→2 means "node 1 trusts node 2", so node 2 influences node 1.
     * Therefore, node 1's opinion is updated based on node 2's opinion.
     * We store in-neighbors: for each node, the list of nodes whose opinions it averages.
     */
    public static class Graph implements Serializable {
        private final Map<Long, List<Long>> inNeighbors;
        private final Map<Long, Integer> inDegree;
        private final Map<Long, Integer> outDegree;
        private final Set<Long> allNodes;

        public Graph(Map<Long, List<Long>> inNeighbors,
                     Map<Long, Integer> inDegree,
                     Map<Long, Integer> outDegree,
                     Set<Long> allNodes) {
            this.inNeighbors = inNeighbors;
            this.inDegree = inDegree;
            this.outDegree = outDegree;
            this.allNodes = allNodes;
        }

        public Map<Long, List<Long>> getInNeighbors() {
            return inNeighbors;
        }

        public Map<Long, Integer> getInDegree() {
            return inDegree;
        }

        public Map<Long, Integer> getOutDegree() {
            return outDegree;
        }

        public Set<Long> getAllNodes() {
            return allNodes;
        }

        public int getNodeCount() {
            return allNodes.size();
        }

        public int getEdgeCount() {
            return inNeighbors.values().stream().mapToInt(List::size).sum();
        }
    }

    /**
     * Load graph from edge list file.
     * Supports both local filesystem and S3 paths.
     *
     * Edge semantics: FromNodeId → ToNodeId means "FromNode trusts ToNode"
     * Therefore, ToNode's opinion influences FromNode.
     * In the adjacency structure, FromNode's in-neighbors list contains ToNode.
     *
     * @param filePath Path to edge list file (local or S3)
     * @return Graph object with adjacency structure
     */
    public static Graph loadGraph(String filePath) throws IOException {
        Map<Long, List<Long>> inNeighbors = new HashMap<>();
        Map<Long, Integer> inDegree = new HashMap<>();
        Map<Long, Integer> outDegree = new HashMap<>();
        Set<Long> allNodes = new HashSet<>();

        Configuration conf = new Configuration();
        Path path = new Path(filePath);
        FileSystem fs = path.getFileSystem(conf);

        try (BufferedReader br = new BufferedReader(new InputStreamReader(fs.open(path)))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty() || line.trim().startsWith("#")) {
                    continue;
                }

                String[] parts = line.trim().split("\\s+");
                if (parts.length < 2) {
                    continue;
                }

                long fromNode = Long.parseLong(parts[0]);
                long toNode = Long.parseLong(parts[1]);

                allNodes.add(fromNode);
                allNodes.add(toNode);

                inNeighbors.computeIfAbsent(fromNode, k -> new ArrayList<>()).add(toNode);

                outDegree.put(fromNode, outDegree.getOrDefault(fromNode, 0) + 1);
                inDegree.put(toNode, inDegree.getOrDefault(toNode, 0) + 1);
            }
        }

        for (Long node : allNodes) {
            inNeighbors.putIfAbsent(node, new ArrayList<>());
            inDegree.putIfAbsent(node, 0);
            outDegree.putIfAbsent(node, 0);
        }

        return new Graph(inNeighbors, inDegree, outDegree, allNodes);
    }

    /**
     * Get a list of all graph variant file paths from a directory.
     * Supports both local filesystem and S3 paths.
     * Expected files:
     * - original.txt
     * - degree_removed_<rate>.txt
     * - community_removed_<rate>.txt
     * - random_removed_<rate>_trial<N>.txt
     */
    public static List<String> getGraphVariantFiles(String directoryPath) {
        List<String> files = new ArrayList<>();

        try {
            Configuration conf = new Configuration();
            Path path = new Path(directoryPath);
            FileSystem fs = path.getFileSystem(conf);

            if (!fs.exists(path) || !fs.getFileStatus(path).isDirectory()) {
                throw new IllegalArgumentException("Directory does not exist: " + directoryPath);
            }

            FileStatus[] fileStatuses = fs.listStatus(path);
            for (FileStatus status : fileStatuses) {
                if (status.isFile() && status.getPath().getName().endsWith(".txt")) {
                    files.add(status.getPath().toString());
                }
            }

            files.sort(String::compareTo);
            return files;
        } catch (IOException e) {
            throw new RuntimeException("Error listing files in directory: " + directoryPath, e);
        }
    }

    /**
     * Extract graph variant name from file path for output labeling
     * Works with both local and S3 paths
     */
    public static String getGraphVariantName(String filePath) {
        Path path = new Path(filePath);
        String fileName = path.getName();
        return fileName.replaceAll("\\.txt$", "");
    }
}
