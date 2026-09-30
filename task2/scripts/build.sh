#!/bin/bash
# Build the project using Maven

set -e

echo "Building Opinion Simulation project..."

# Clean and package
mvn clean package

echo "Build completed successfully!"
echo "JAR location: target/opinion-simulation-1.0-SNAPSHOT.jar"
