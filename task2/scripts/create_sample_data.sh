#!/bin/bash

# Create sample graph data for testing
# This creates a small example directory with toy graphs

SAMPLE_DIR="data/sample_graphs"
mkdir -p "$SAMPLE_DIR"

echo "Creating sample graph files in $SAMPLE_DIR/"

# Create a small original graph (20 nodes, ~40 edges)
cat > "$SAMPLE_DIR/original.txt" <<'EOF'
# Sample trust network
# Format: FromNodeId	ToNodeId
1	2
1	3
2	3
2	4
3	5
4	5
5	6
6	7
7	8
8	9
9	10
10	1
11	12
12	13
13	14
14	15
15	16
16	17
17	18
18	19
19	20
20	11
1	11
11	1
5	15
15	5
EOF

# Create a "degree removed" variant (remove a few high-degree edges)
cat > "$SAMPLE_DIR/degree_removed_0.1.txt" <<'EOF'
# Degree removed variant
1	2
1	3
2	4
3	5
4	5
5	6
6	7
7	8
8	9
9	10
11	12
12	13
13	14
14	15
15	16
16	17
17	18
18	19
19	20
1	11
11	1
5	15
15	5
EOF

# Create a "random removed" variant
cat > "$SAMPLE_DIR/random_removed_0.1_trial1.txt" <<'EOF'
# Random removed variant - trial 1
1	2
2	3
2	4
3	5
5	6
6	7
7	8
8	9
9	10
10	1
11	12
12	13
14	15
15	16
16	17
17	18
18	19
19	20
20	11
1	11
5	15
EOF

echo "Sample graph files created:"
ls -lh "$SAMPLE_DIR"

echo ""
echo "To test with this sample data:"
echo "  Sequential: ./run_sequential.sh $SAMPLE_DIR output/sample_results_sequential.csv"
echo "  Spark:      ./run_spark_local.sh $SAMPLE_DIR output/sample_results_spark.csv"
