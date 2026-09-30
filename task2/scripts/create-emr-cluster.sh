#!/bin/bash
# Create EMR cluster for running Spark jobs

set -e

# Load configuration
CONFIG_FILE="../config/aws-config.properties"

if [ ! -f "$CONFIG_FILE" ]; then
    echo "Error: Configuration file not found at $CONFIG_FILE"
    exit 1
fi

# Read configuration (simplified - in practice, use proper parsing)
CLUSTER_NAME=${EMR_CLUSTER_NAME:-"OpinionSimulation-Cluster"}
REGION=${AWS_REGION:-"us-east-1"}
KEY_NAME=${EC2_KEY_NAME:-"your-ec2-keypair"}
LOG_URI=${EMR_LOG_URI:-"s3://cs6240-opinion-simulation/logs/"}

echo "Creating EMR cluster: $CLUSTER_NAME"

# Create cluster using the JSON configuration
CLUSTER_ID=$(aws emr create-cluster \
    --cli-input-json file://../config/emr-cluster-config.json \
    --region $REGION \
    --query 'ClusterId' \
    --output text)

echo "Cluster created with ID: $CLUSTER_ID"
echo "Waiting for cluster to be ready..."

# Wait for cluster to be ready
aws emr wait cluster-running --cluster-id $CLUSTER_ID --region $REGION

echo "Cluster is now running!"
echo "Cluster ID: $CLUSTER_ID"
echo ""
echo "To check status:"
echo "  aws emr describe-cluster --cluster-id $CLUSTER_ID --region $REGION"
echo ""
echo "To terminate cluster:"
echo "  aws emr terminate-clusters --cluster-ids $CLUSTER_ID --region $REGION"
