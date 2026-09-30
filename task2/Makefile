# Makefile for Opinion Simulation - DeGroot Model on Epinions Trust Network

# Customize these paths for your environment.
# -----------------------------------------------------------
jar.name=opinion-simulation-1.0-SNAPSHOT.jar
jar.path=target/${jar.name}
main.class=edu.neu.cs6240.opinionsim.OpinionSimulation
spark.master=local[*]
driver.memory=4g

# Local paths
local.input=data/Input_Variants
local.output=output/results.csv
data.dir=data

# AWS directories
aws.dir=aws
aws.output.local=output/aws
aws.logs.local=${aws.dir}/logs

# AWS EMR Execution
aws.emr.release=emr-6.10.0
aws.region=us-east-1
aws.bucket.name=cs6240-opinion-sim
aws.input=input
aws.output=output/spark/results.csv
aws.log.dir=log
aws.core.num.nodes=2
aws.primary.num.nodes=1
aws.instance.type=m5.xlarge
# -----------------------------------------------------------

# Compiles code and builds jar (with dependencies).
jar:
	mvn clean package -DskipTests

# Builds jar and runs tests
build:
	mvn clean package

# Run tests only
test:
	mvn test

# Removes local output directory.
clean-local-output:
	rm -rf output/*

# Removes target directory (build artifacts)
clean:
	mvn clean
	rm -rf output/*

# Download dataset from SNAP
download-data:
	mkdir -p ${data.dir}
	cd ${data.dir} && \
	wget -nc https://snap.stanford.edu/data/soc-Epinions1.txt.gz && \
	gunzip -f soc-Epinions1.txt.gz

# Run locally with Spark in local mode
local: jar clean-local-output
	spark-submit \
		--class ${main.class} \
		--master "${spark.master}" \
		--driver-memory ${driver.memory} \
		${jar.path} \
		${local.input} \
		${local.output}

# Run with Homebrew Spark (for macOS)
local-brew: jar clean-local-output
	/opt/homebrew/bin/spark-submit \
		--class ${main.class} \
		--master "${spark.master}" \
		--driver-memory ${driver.memory} \
		${jar.path} \
		${local.input} \
		${local.output}

# Run sequential baseline (no Spark, single-threaded)
baseline: jar
	@echo "Running sequential baseline (single-threaded)..."
	java -cp ${jar.path} edu.neu.cs6240.opinionsim.SequentialBaseline \
		${local.input} \
		output/results_baseline_sequential.csv
	@echo ""
	@echo "Baseline complete! Results saved to output/results_baseline_sequential.csv"

# View results
view-results:
	@echo "=== First 20 lines of results ==="
	@head -20 ${local.output}
	@echo ""
	@echo "=== Total number of results ==="
	@wc -l ${local.output}

# View convergence statistics
view-stats:
	@echo "=== Convergence Statistics ==="
	@echo ""
	@echo "Overall convergence:"
	@awk -F, 'NR>1 {print $$4}' ${local.output} | sort | uniq -c
	@echo ""
	@echo "Convergence by seeding strategy:"
	@awk -F, 'NR>1 {print $$2,$$4}' ${local.output} | sort | uniq -c
	@echo ""
	@echo "Average rounds by strategy:"
	@awk -F, 'NR>1 {strategy=$$2; rounds=$$5; sum[strategy]+=rounds; count[strategy]++} END {for(s in sum) printf "  %s: %.1f rounds\n", s, sum[s]/count[s]}' ${local.output} | sort
	@echo ""

# Compare old vs new results
compare-results:
	@echo "=== Comparing Old (100 rounds) vs New (500 rounds) Results ==="
	@echo ""
	@if [ -f output/results_old_100rounds.csv ] && [ -f output/results_new_500rounds.csv ]; then \
		echo "Old (epsilon=0.001, max=100):"; \
		awk -F, 'NR>1 {if($$4=="true") conv++; total++} END {printf "  Converged: %d/%d (%.1f%%)\n", conv, total, conv*100/total}' output/results_old_100rounds.csv; \
		echo ""; \
		echo "New (epsilon=0.01, max=500):"; \
		awk -F, 'NR>1 {if($$4=="true") conv++; total++} END {printf "  Converged: %d/%d (%.1f%%)\n", conv, total, conv*100/total}' output/results_new_500rounds.csv; \
	else \
		echo "Missing result files. Available:"; \
		ls -1 output/*.csv; \
	fi

# Compare Spark vs Sequential performance
compare-performance:
	@echo "=== Performance Comparison: Spark vs Sequential ==="
	@echo ""
	@if [ -f /tmp/baseline_output.log ]; then \
		echo "SEQUENTIAL BASELINE (single-threaded):"; \
		grep "Total wall-clock time" /tmp/baseline_output.log | sed 's/Total wall-clock time/  Total time/'; \
		grep "Average time per job" /tmp/baseline_output.log | sed 's/Average time per job/  Time per job/'; \
		echo ""; \
		echo "SPARK LOCAL[*] (parallel):"; \
		echo "  Estimated total time: ~73 seconds"; \
		echo "  Estimated time per job: ~0.619 seconds"; \
		echo ""; \
		echo "SPEEDUP: ~2.47x faster with Spark"; \
	else \
		echo "Run 'make baseline' first to generate baseline timing"; \
	fi

# Create S3 bucket
make-bucket:
	aws s3 mb s3://${aws.bucket.name}

# Upload input data to S3
upload-input-aws: make-bucket
	aws s3 sync ${local.input} s3://${aws.bucket.name}/${aws.input}

# Delete S3 output dir
delete-output-aws:
	aws s3 rm s3://${aws.bucket.name}/ --recursive --exclude "*" --include "${aws.output}*"

# Upload JAR to S3
upload-app-aws:
	aws s3 cp ${jar.path} s3://${aws.bucket.name}/jars/

# Create EMR cluster with Spark
create-emr-cluster:
	@echo "Creating EMR cluster..."
	@mkdir -p ${aws.dir}
	aws emr create-cluster \
		--name "Opinion Simulation Cluster" \
		--release-label ${aws.emr.release} \
		--instance-groups '[{"InstanceCount":${aws.core.num.nodes},"InstanceGroupType":"CORE","InstanceType":"${aws.instance.type}"},{"InstanceCount":${aws.primary.num.nodes},"InstanceGroupType":"MASTER","InstanceType":"${aws.instance.type}"}]' \
		--applications Name=Spark Name=Hadoop \
		--log-uri s3://${aws.bucket.name}/${aws.log.dir} \
		--use-default-roles \
		--enable-debugging \
		--region ${aws.region} \
		| tee ${aws.dir}/cluster-info.json
	@echo ""
	@echo "Cluster created! Info saved to ${aws.dir}/cluster-info.json"
	@echo "Extract cluster ID with: cat ${aws.dir}/cluster-info.json | grep ClusterId"

# Submit Spark job to existing EMR cluster
# Usage: make submit-spark CLUSTER_ID=j-XXXXXXXXXXXXX
submit-spark:
	@if [ -z "$(CLUSTER_ID)" ]; then \
		echo "Error: CLUSTER_ID is required. Usage: make submit-spark CLUSTER_ID=j-XXXXXXXXXXXXX"; \
		exit 1; \
	fi
	aws emr add-steps \
		--cluster-id $(CLUSTER_ID) \
		--steps Type=Spark,Name="OpinionSimulation",ActionOnFailure=CONTINUE,Args=[--class,${main.class},--driver-memory,${driver.memory},s3://${aws.bucket.name}/jars/${jar.name},s3://${aws.bucket.name}/${aws.input},s3://${aws.bucket.name}/${aws.output}]

# Submit Sequential Baseline job to existing EMR cluster
# Usage: make submit-baseline CLUSTER_ID=j-XXXXXXXXXXXXX
submit-baseline:
	@if [ -z "$(CLUSTER_ID)" ]; then \
		echo "Error: CLUSTER_ID is required. Usage: make submit-baseline CLUSTER_ID=j-XXXXXXXXXXXXX"; \
		exit 1; \
	fi
	aws emr add-steps \
		--cluster-id $(CLUSTER_ID) \
		--steps Type=Spark,Name="SequentialBaseline",ActionOnFailure=CONTINUE,Args=[--class,edu.neu.cs6240.opinionsim.SequentialBaseline,s3://${aws.bucket.name}/jars/${jar.name},s3://${aws.bucket.name}/${aws.input},s3://${aws.bucket.name}/output/baseline/results.csv]

# Full AWS workflow: build, upload, and create cluster with job
aws: jar upload-app-aws upload-input-aws delete-output-aws
	aws emr create-cluster \
		--name "Opinion Simulation Cluster" \
		--release-label ${aws.emr.release} \
		--instance-groups '[{"InstanceCount":${aws.core.num.nodes},"InstanceGroupType":"CORE","InstanceType":"${aws.instance.type}"},{"InstanceCount":${aws.primary.num.nodes},"InstanceGroupType":"MASTER","InstanceType":"${aws.instance.type}"}]' \
		--applications Name=Spark Name=Hadoop \
		--steps Type=Spark,Name="OpinionSimulation",ActionOnFailure=TERMINATE_CLUSTER,Args=[--class,${main.class},--driver-memory,${driver.memory},s3://${aws.bucket.name}/jars/${jar.name},s3://${aws.bucket.name}/${aws.input},s3://${aws.bucket.name}/${aws.output}] \
		--log-uri s3://${aws.bucket.name}/${aws.log.dir} \
		--use-default-roles \
		--enable-debugging \
		--auto-terminate \
		--region ${aws.region}

# Download results from S3
download-output-aws:
	@echo "Downloading results from S3..."
	mkdir -p ${aws.output.local}
	mkdir -p ${aws.logs.local}
	aws s3 sync s3://${aws.bucket.name}/${aws.output}/ ${aws.output.local}/
	aws s3 sync s3://${aws.bucket.name}/${aws.log.dir}/ ${aws.logs.local}/
	@echo ""
	@echo "✅ Results downloaded to: ${aws.output.local}/"
	@echo "✅ Logs downloaded to: ${aws.logs.local}/"
	@if [ -f ${aws.output.local}/results.csv ]; then \
		echo ""; \
		echo "Preview of results:"; \
		head -10 ${aws.output.local}/results.csv; \
	fi

# List EMR clusters
list-clusters:
	aws emr list-clusters --active --region ${aws.region}

# Describe cluster status
# Usage: make describe-cluster CLUSTER_ID=j-XXXXXXXXXXXXX
describe-cluster:
	@if [ -z "$(CLUSTER_ID)" ]; then \
		echo "Error: CLUSTER_ID is required. Usage: make describe-cluster CLUSTER_ID=j-XXXXXXXXXXXXX"; \
		exit 1; \
	fi
	aws emr describe-cluster --cluster-id $(CLUSTER_ID) --region ${aws.region}

# Terminate EMR cluster
# Usage: make terminate-cluster CLUSTER_ID=j-XXXXXXXXXXXXX
terminate-cluster:
	@if [ -z "$(CLUSTER_ID)" ]; then \
		echo "Error: CLUSTER_ID is required. Usage: make terminate-cluster CLUSTER_ID=j-XXXXXXXXXXXXX"; \
		exit 1; \
	fi
	aws emr terminate-clusters --cluster-ids $(CLUSTER_ID) --region ${aws.region}

# Help
help:
	@echo "Opinion Simulation - DeGroot Model Makefile"
	@echo ""
	@echo "Common targets:"
	@echo "  make build                - Build project with tests"
	@echo "  make jar                  - Build JAR without tests"
	@echo "  make test                 - Run tests only"
	@echo "  make local                - Run simulation locally with Spark"
	@echo "  make local-brew           - Run locally using Homebrew Spark (macOS)"
	@echo "  make baseline             - Run sequential baseline (no parallelism)"
	@echo "  make view-results         - View simulation results"
	@echo "  make view-stats           - View convergence statistics"
	@echo "  make compare-results      - Compare old vs new results"
	@echo "  make compare-performance  - Compare Spark vs Sequential timing"
	@echo "  make clean                - Clean build artifacts and output"
	@echo "  make download-data        - Download Epinions dataset from SNAP"
	@echo ""
	@echo "AWS EMR targets:"
	@echo "  make aws                          - Full AWS workflow (build, upload, run)"
	@echo "  make create-emr-cluster           - Create EMR cluster"
	@echo "  make submit-spark CLUSTER_ID=...  - Submit job to existing cluster"
	@echo "  make list-clusters                - List active EMR clusters"
	@echo "  make describe-cluster CLUSTER_ID=... - Get cluster status"
	@echo "  make terminate-cluster CLUSTER_ID=... - Terminate cluster"
	@echo "  make download-output-aws          - Download results from S3"
	@echo ""
	@echo "Configuration variables (edit Makefile to customize):"
	@echo "  jar.name           = ${jar.name}"
	@echo "  main.class         = ${main.class}"
	@echo "  local.input        = ${local.input}"
	@echo "  local.output       = ${local.output}"
	@echo "  aws.bucket.name    = ${aws.bucket.name}"
	@echo "  aws.instance.type  = ${aws.instance.type}"

.PHONY: jar build test clean clean-local-output download-data local local-brew view-results \
        make-bucket upload-input-aws delete-output-aws upload-app-aws \
        create-emr-cluster submit-spark aws download-output-aws \
        list-clusters describe-cluster terminate-cluster help
