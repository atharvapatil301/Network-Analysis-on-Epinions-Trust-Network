#!/bin/bash
# Submit Spark job to EMR cluster

set -e

# Parse arguments
if [ $# -lt 3 ]; then
    echo "Usage: $0 <cluster-id> <input-path> <output-path> [seeding-strategy]"
    echo "  cluster-id: EMR cluster ID"
    echo "  input-path: S3 path to Epinions dataset"
    echo "  output-path: S3 path for output"
    echo "  seeding-strategy: UNIFORM_RANDOM, BIMODAL, SINGLE_SEED (optional, default: UNIFORM_RANDOM)"
    exit 1
fi

CLUSTER_ID=$1
INPUT_PATH=$2
OUTPUT_PATH=$3
SEEDING_STRATEGY=${4:-"UNIFORM_RANDOM"}
REGION=${AWS_REGION:-"us-east-1"}
JAR_PATH=${JAR_S3_PATH:-"s3://cs6240-opinion-simulation/jars/opinion-simulation-1.0-SNAPSHOT.jar"}

echo "Submitting Spark job to cluster: $CLUSTER_ID"
echo "Input: $INPUT_PATH"
echo "Output: $OUTPUT_PATH"
echo "Seeding Strategy: $SEEDING_STRATEGY"

# Submit step to EMR
STEP_ID=$(aws emr add-steps \
    --cluster-id $CLUSTER_ID \
    --region $REGION \
    --steps Type=Spark,Name="OpinionSimulation-$SEEDING_STRATEGY",ActionOnFailure=CONTINUE,Args=[--class,edu.neu.cs6240.opinionsim.OpinionSimulation,--master,yarn,--deploy-mode,cluster,--driver-memory,2g,--executor-memory,4g,--executor-cores,2,$JAR_PATH,$INPUT_PATH,$OUTPUT_PATH,$SEEDING_STRATEGY] \
    --query 'StepIds[0]' \
    --output text)

echo "Step submitted with ID: $STEP_ID"
echo ""
echo "To monitor step:"
echo "  aws emr describe-step --cluster-id $CLUSTER_ID --step-id $STEP_ID --region $REGION"
echo ""
echo "To view logs:"
echo "  aws emr ssh --cluster-id $CLUSTER_ID --region $REGION --key-pair-file ~/.ssh/your-key.pem"
