#!/bin/bash

# Sequential baseline runner script
# Usage: ./run_sequential.sh <input-directory> <output-csv-path>

if [ $# -lt 2 ]; then
    echo "Usage: $0 <input-directory> <output-csv-path>"
    echo "  input-directory: Directory containing graph variant .txt files"
    echo "  output-csv-path: Path to output CSV file"
    exit 1
fi

INPUT_DIR=$1
OUTPUT_CSV=$2

echo "=== Running Sequential Baseline ==="
echo "Input directory: $INPUT_DIR"
echo "Output CSV: $OUTPUT_CSV"
echo ""

# Build if jar doesn't exist
if [ ! -f target/opinion-simulation-1.0-SNAPSHOT.jar ]; then
    echo "JAR not found, building project..."
    mvn clean package -DskipTests
    echo ""
fi

# Run sequential baseline
java -cp target/opinion-simulation-1.0-SNAPSHOT.jar \
    edu.neu.cs6240.opinionsim.SequentialBaseline \
    "$INPUT_DIR" \
    "$OUTPUT_CSV"

echo ""
echo "=== Sequential Baseline Complete ==="
echo "Results saved to: $OUTPUT_CSV"
