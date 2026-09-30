# Opinion Simulation - DeGroot Model on Epinions Trust Network

CS6240 Final Project - Ban-Wave Robustness & Opinion Dynamics on a Social Trust Network

## Project Overview

This project empirically tests network robustness and opinion dynamics on the Epinions trust network. It consists of two major tasks:

1. **Ban-Wave Analysis**: Tests whether scale-free networks are robust to random failure but fragile to targeted attack by simulating 10% node removal under different strategies
2. **Opinion Dynamics**: Simulates DeGroot consensus model on graph variants to test whether structural fragmentation affects convergence behavior

### Key Results

- **Task 1**: Targeted hub removal collapses connectivity from 100% to 38.75%, while random/community removal preserves 98.6-99.98%
- **Task 2**: 93.2% non-convergence across all variants, with fragmentation amplifying variance 3-6× but not affecting convergence

### Components

- **Dataset**: Epinions trust network (75,879 nodes, 508,837 edges)
- **Framework**: Apache Spark (Java 8)
- **Deployment**: AWS EMR (Elastic MapReduce)
- **Speedup**: 2.93× (Spark vs Sequential on EMR)

## Project Structure

```
.
├── src/main/java/edu/neu/cs6240/opinionsim/
│   ├── OpinionSimulation.java        # Main Spark driver (parallel)
│   ├── SequentialBaseline.java       # Sequential baseline (no Spark)
│   ├── GraphLoader.java              # S3-compatible graph loading
│   ├── OpinionInitializer.java       # Seeding strategies
│   ├── DeGrootSimulator.java         # DeGroot iteration logic
│   ├── OutputRow.java                # Result formatting
│   └── SimulationJob.java            # Job definition
├── data/Input_Variants/              # 8 graph variant files
├── output/aws/                       # AWS execution results
│   ├── results.csv                   # Spark results (118 simulations)
│   └── baseline-results.csv          # Sequential baseline (verified identical)
├── aws/logs/                         # AWS EMR logs
│   ├── spark-success/                # Successful Spark job logs
│   └── baseline-success/             # Successful baseline logs
├── pom.xml                           # Maven build (Java 8 target)
├── Makefile                          # Build and deployment automation
├── README.md                         # This file
└── FINAL_REPORT_CONTENT.docx         # Final project report (7 pages)
```

## Prerequisites

### Local Development

- **Java 8** (EMR 6.10.0 compatibility requirement)
- Maven 3.6+
- Apache Spark 3.3+ (for local testing)

### AWS Deployment

- AWS CLI configured with credentials
- AWS account with EMR and S3 access
- S3 bucket: `cs6240-opinion-sim`

## Build Instructions

### Using Makefile (Recommended)

```bash
# Build JAR (without tests)
make jar

# Build with tests
make build

# Run tests only
make test
```

### Using Maven Directly

```bash
# Build without tests
mvn clean package -DskipTests

# Build with tests
mvn clean package
```

Output: `target/opinion-simulation-1.0-SNAPSHOT.jar`

## Execution Instructions

### Local Execution (Standalone)

#### Spark Parallel Version

```bash
# Using Makefile
make local-brew

# Or manually
spark-submit \
  --class edu.neu.cs6240.opinionsim.OpinionSimulation \
  --master "local[*]" \
  --driver-memory 4g \
  target/opinion-simulation-1.0-SNAPSHOT.jar \
  data/Input_Variants \
  output/results.csv
```

#### Sequential Baseline (No Spark)

```bash
# Using Makefile
make baseline

# Or manually
java -cp target/opinion-simulation-1.0-SNAPSHOT.jar \
  edu.neu.cs6240.opinionsim.SequentialBaseline \
  data/Input_Variants \
  output/results_baseline.csv
```

**Expected Runtime (Local)**:
- Spark parallel: ~73 seconds (2.47× speedup)
- Sequential baseline: ~180 seconds

### AWS EMR Execution

#### 1. Build and Upload

```bash
# Build JAR
make jar

# Upload JAR to S3
make upload-app-aws

# Upload input data to S3
make upload-input-aws
```

#### 2. Create EMR Cluster

```bash
make create-emr-cluster
# Outputs cluster ID: j-XXXXXXXXXXXXX
```

**Cluster Configuration**:
- Release: EMR 6.10.0
- Applications: Spark 3.3.1, Hadoop 3.3.3
- Instance Type: m5.xlarge (4 vCPU, 16 GB RAM)
- Cluster Size: 1 master + 2 core nodes
- Region: us-east-1

#### 3. Submit Jobs

**Spark Parallel Job**:
```bash
make submit-spark CLUSTER_ID=j-XXXXXXXXXXXXX
```

**Sequential Baseline Job**:
```bash
make submit-baseline CLUSTER_ID=j-XXXXXXXXXXXXX
```

#### 4. Monitor Execution

```bash
# List active clusters
make list-clusters

# Check cluster status
make describe-cluster CLUSTER_ID=j-XXXXXXXXXXXXX

# Check step status
aws emr list-steps --cluster-id j-XXXXXXXXXXXXX
```

#### 5. Download Results

```bash
make download-output-aws
```

Results downloaded to:
- `output/aws/results.csv` (Spark results)
- `output/aws/baseline-results.csv` (Sequential baseline)
- `aws/logs/` (Execution logs)

#### 6. Terminate Cluster

```bash
make terminate-cluster CLUSTER_ID=j-XXXXXXXXXXXXX
```

**Expected Runtime (AWS EMR)**:
- Spark parallel: ~264 seconds (2.93× speedup)
- Sequential baseline: ~774 seconds

## Implementation Details

### DeGroot Model

1. **Initialization**: Assign opinions based on seeding strategy
2. **Iteration**: Each node updates opinion as weighted average of in-neighbors:
   ```
   opinion_i(t+1) = average(opinions of in-neighbors)
   ```
3. **Convergence**: Detect when `max_change < epsilon` (0.01) or reach max rounds (500)
4. **Output**: Convergence status, rounds, final variance, final mean

### Seeding Strategies

1. **UNIFORM_RANDOM**: Opinions uniformly distributed in [-1, 1]
2. **BIMODAL_COMMUNITY**: Detected communities initialized to ±1 (polarized)
3. **SINGLE_SEED_HIGHDEGREE**: Highest-degree node = +1, all others = 0

### Spark Architecture

**Embarrassingly Parallel Design**:
- Broadcast graphs to all executors (~100 MB total)
- Parallelize 118 independent simulation jobs
- Local iteration within executors (no shuffles)
- Avoids 4000+ shuffle phases vs. iterative RDD approach

### AWS S3 Compatibility

All file I/O uses Hadoop FileSystem API for S3 compatibility:
- `GraphLoader.java`: S3 directory listing and graph loading
- `OpinionSimulation.java`: S3 CSV output
- `SequentialBaseline.java`: S3 CSV output

## Results

### Execution Evidence

**Spark Job** (Step ID: s-06189562YIW18FU0AD3D):
- Runtime: 264 seconds
- Jobs: 118 simulations


**Sequential Baseline** (Step ID: s-0716503QMVAJY5GWCZA):
- Runtime: 774 seconds
- Jobs: 118 simulations


**Verification**:
```bash
diff output/aws/results.csv output/aws/baseline-results.csv
# No differences - results are identical
```

### Key Findings

- **Convergence Rate**: 6.8% (8/118 simulations)
- **Non-Convergence**: 93.2% (110/118 simulations)
- **Speedup**: 2.93× (Spark vs Sequential on EMR)
- **Variance Amplification**: 3-6× for degree-removed graphs

## Makefile Targets

### Build Targets
- `make jar` - Build JAR without tests
- `make build` - Build JAR with tests
- `make test` - Run tests only
- `make clean` - Clean build artifacts and output

### Local Execution Targets
- `make local` - Run Spark locally (requires spark-submit in PATH)
- `make local-brew` - Run Spark using Homebrew installation (macOS)
- `make baseline` - Run sequential baseline (no Spark)
- `make view-results` - View first 20 lines of results
- `make view-stats` - View convergence statistics

### AWS Targets
- `make upload-app-aws` - Upload JAR to S3
- `make upload-input-aws` - Upload input data to S3
- `make create-emr-cluster` - Create EMR cluster
- `make submit-spark CLUSTER_ID=...` - Submit Spark job
- `make submit-baseline CLUSTER_ID=...` - Submit baseline job
- `make download-output-aws` - Download results from S3
- `make list-clusters` - List active EMR clusters
- `make describe-cluster CLUSTER_ID=...` - Get cluster status
- `make terminate-cluster CLUSTER_ID=...` - Terminate cluster

### Help
- `make help` - Display all available targets

## Troubleshooting

### Java Version Issues

**Error**: `UnsupportedClassVersionError: class file version 55.0`

**Solution**: EMR 6.10.0 requires Java 8. Verify `pom.xml`:
```xml
<maven.compiler.source>1.8</maven.compiler.source>
<maven.compiler.target>1.8</maven.compiler.target>
```

### S3 Access Issues

**Error**: `IllegalArgumentException: Directory does not exist`

**Solution**: Code uses Hadoop FileSystem API for S3. Ensure:
- S3 bucket exists: `cs6240-opinion-sim`
- AWS credentials configured
- Bucket permissions allow access

### Build Issues

```bash
# Clean Maven cache
mvn clean install -U

# Verify Java version
java -version  # Should show 1.8.x
```

## References

- **Final Report**: `FINAL_REPORT_CONTENT.docx` (7 pages)
- **Dataset**: [Epinions Trust Network (SNAP)](https://snap.stanford.edu/data/soc-Epinions1.html)
- **DeGroot Model**: [Wikipedia](https://en.wikipedia.org/wiki/DeGroot_learning)
- **Apache Spark**: [Documentation](https://spark.apache.org/docs/latest/)
- **AWS EMR**: [User Guide](https://docs.aws.amazon.com/emr/latest/ManagementGuide/)


