#!/bin/bash
# Setup S3 bucket and upload necessary files

set -e

# Configuration
BUCKET_NAME=${1:-"cs6240-opinion-simulation"}
REGION=${2:-"us-east-1"}

echo "Setting up S3 bucket: $BUCKET_NAME in region $REGION"

# Create S3 bucket
aws s3 mb s3://$BUCKET_NAME --region $REGION

# Create folder structure
aws s3api put-object --bucket $BUCKET_NAME --key input/ --region $REGION
aws s3api put-object --bucket $BUCKET_NAME --key output/ --region $REGION
aws s3api put-object --bucket $BUCKET_NAME --key jars/ --region $REGION
aws s3api put-object --bucket $BUCKET_NAME --key logs/ --region $REGION

echo "S3 bucket setup completed!"
echo ""
echo "Next steps:"
echo "1. Upload Epinions dataset: aws s3 cp data/soc-Epinions1.txt s3://$BUCKET_NAME/input/"
echo "2. Upload JAR: aws s3 cp target/opinion-simulation-1.0-SNAPSHOT.jar s3://$BUCKET_NAME/jars/"
