# Ban-Wave Robustness & Opinion Dynamics on a Social Trust Network



## Project Layout

```
src/main/scala/
  GraphVariantGenerator.scala   Task 1: builds original + degree/community/random
                                 removed graph variants (Spark, complete)
  ConnectedComponents.scala     Task 1: connected-components stats per variant
                                 via iterative min-label propagation (Spark, complete)
data/
  soc-Epinions1_sample.txt      small synthetic sample for local testing
  soc-Epinions1.txt             full dataset (not committed -- download separately, see below)
build.sbt
project/plugins.sbt            sbt-assembly plugin
ec2/install-spark.sh           bootstrap script for standalone EC2 Spark cluster
Makefile
README.md
```

Task 2 (DeGroot opinion dynamics, consuming the graph variants produced above) is being
developed separately -- see the project report for its design and pseudocode.

## Requirements

- sbt 1.9+, Scala 2.12.17
- Apache Spark 3.4.1 (`spark-submit` on your `PATH` for local runs)
- AWS CLI configured with credentials that can create EMR clusters and S3 buckets

## Dataset

This project uses `soc-Epinions1` from SNAP (75,879 nodes, 508,837 directed trust edges).
Not committed to the repo due to size -- download it yourself:

```
curl -O https://snap.stanford.edu/data/soc-Epinions1.txt.gz
gunzip soc-Epinions1.txt.gz
mv soc-Epinions1.txt data/
```

## Build

```
sbt assembly
```

Produces `target/scala-2.12/project2-assembly-1.0.jar`.

Before running on AWS, edit the top of `Makefile`:
- `aws.bucket.name` -- your S3 bucket (must be globally unique)
- `aws.subnet.id` -- a subnet ID from your AWS VPC
- `aws.region` -- defaults to `us-east-1`

## Running Locally

Generate the 8 graph variants (original, degree-removed, community-removed, 5 random-removal trials):
```
make local-job1
```
Output lands in `output/variants/`.

Compute connected-components stats for one variant:
```
make local-cc VARIANT=original
make local-cc VARIANT=degree_removed_10pct
```
(swap `VARIANT` for any of: `original`, `degree_removed_10pct`, `community_removed_10pct`,
`random_removed_10pct_trial1` through `trial5`)

Output lands in `output/cc-stats/<variant>/part-00000`, one line:
```
total_nodes,num_components,largest_component_size,largest_component_fraction,iterations_run
```

## Running on AWS EMR

Generate all 8 variants on a cluster:
```
make aws-emr-job1
```

Compute connected-components stats for one variant:
```
make aws-emr-cc VARIANT=degree_removed_10pct
```

Compute connected-components stats for **all 8 variants** as sequential steps on a single
cluster (avoids paying cluster-boot overhead 8 times):
```
make aws-emr-cc-all
make aws-emr-cc-all-fetch   # pulls all 8 results back down afterward
```

Speedup/scalability comparison targets used for the report:
```
make aws-emr-cc-speedup-small       # original.txt, 2 core nodes
make aws-emr-cc-speedup-large       # original.txt, 4 core nodes
make aws-emr-cc-scalability-small   # degree_removed_10pct.txt, 4 core nodes
```

Each EMR run auto-terminates its cluster on completion. Clusters use 64GB EBS volumes per
node and checkpoint to HDFS (not local disk) -- both fixes for real issues hit during
development; see the project report's Algorithm and Program Analysis section.

## Input Data Format

Raw SNAP edge list, tab-separated, `#`-prefixed comment header:
```
# FromNodeId	ToNodeId
0	4
0	5
```

## Output Format

**Graph variants** (`output/variants/<name>.txt`): same tab-separated `from\tto` format as input.

**Connected-components stats** (`output/cc-stats/<variant>/part-00000`): single CSV line,
`total_nodes,num_components,largest_component_size,largest_component_fraction,iterations_run`.

## Status

- **Task 1 (Ban-Wave Node Removal & Connectivity Analysis):** complete. All 8 graph variants
  generated and validated; connected-components computed and verified correct for all 8,
  both locally and on EMR. Speedup (2 vs. 4 core nodes) and scalability (large vs. small
  input) results collected.
- **Task 2 (DeGroot Opinion Dynamics):** design complete (see project report); implementation
  in progress separately.


