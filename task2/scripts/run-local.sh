#!/bin/bash
# Run the simulation locally for testing

set -e

# Check if JAR exists
JAR_FILE="../target/opinion-simulation-1.0-SNAPSHOT.jar"

if [ ! -f "$JAR_FILE" ]; then
    echo "JAR file not found. Building project..."
    ./build.sh
fi

# Parse arguments
INPUT_PATH=${1:-"../data/soc-Epinions1.txt"}
OUTPUT_PATH=${2:-"../output/local-test"}
SEEDING_STRATEGY=${3:-"UNIFORM_RANDOM"}

echo "Running Opinion Simulation locally..."
echo "Input: $INPUT_PATH"
echo "Output: $OUTPUT_PATH"
echo "Seeding Strategy: $SEEDING_STRATEGY"

# Run with Spark local mode
spark-submit \
    --class edu.neu.cs6240.opinionsim.OpinionSimulation \
    --master local[*] \
    --driver-memory 2g \
    $JAR_FILE \
    $INPUT_PATH \
    $OUTPUT_PATH \
    $SEEDING_STRATEGY

echo "Simulation completed!"
echo "Results available at: $OUTPUT_PATH"
