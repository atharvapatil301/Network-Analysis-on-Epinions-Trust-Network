# Makefile for CS6240 Project 2
# Two generic job slots (JOB1/JOB2) so this works for either candidate
# project without restructuring:
#   Project A (trust-cascade):        JOB1=GraphPreprocessing   JOB2=CascadeSimulation
#   Project B (ban-wave/opinion-dyn): JOB1=BanWaveRemoval       JOB2=OpinionDynamics
# Swap the two CLASS variables below to point at whichever pair you finalize.

# -----------------------------------------------------------
jar.name=project2-assembly-1.0.jar
jar.path=target/scala-2.12/${jar.name}

job1.class=GraphVariantGenerator
job1b.class=ConnectedComponents
job2.class=DeGrootOpinionDynamics
# job2.class is a placeholder -- set this to whatever class name your
# partner's DeGroot implementation actually uses.

# Which variant to run ConnectedComponents against, e.g.:
#   make local-cc VARIANT=original
#   make local-cc VARIANT=degree_removed_10pct
#   make local-cc VARIANT=random_removed_10pct_trial1
VARIANT=original

local.input=data
local.output=output

# Job parameters (edit per run)
k.trials=5

# -------------------- AWS shared config --------------------
aws.region=us-east-1
aws.bucket.name=cs6240-project2-adit-atharva
aws.input=input
aws.output=output
aws.log.dir=log

# -------------------- EMR config --------------------
aws.emr.release=emr-6.10.0
aws.subnet.id=subnet-04eac630b811dc0d7
aws.emr.num.core.nodes=4
aws.emr.num.master.nodes=1
aws.emr.instance.type=m5.xlarge

# -------------------- EC2 (standalone Spark cluster) config --------------------
ec2.ami=ami-0c7217cdde317cfec
ec2.key.name=CHANGE-ME-keypair
ec2.key.path=~/.ssh/CHANGE-ME-keypair.pem
ec2.instance.type=m5.xlarge
ec2.security.group=CHANGE-ME-sg-id
ec2.num.workers=3
ec2.master.tag=project2-spark-master
ec2.worker.tag=project2-spark-worker
ec2.spark.version=3.4.1
ec2.spark.hadoop.version=3
# -----------------------------------------------------------

# Build jar with sbt
jar:
	sbt assembly

# Remove local output
clean-local-output:
	rm -rf ${local.output}*

# -------------------- LOCAL RUNS --------------------

# Task 1: generates original.txt, degree_removed_10pct.txt,
# community_removed_10pct.txt, random_removed_10pct_trial1..${k.trials}.txt
# in ${local.output}/variants/
local-job1: jar clean-local-output
	spark-submit --class ${job1.class} --master local[*] \
		--conf spark.local.dir=${local.output}/spark-tmp \
		${jar.path} \
		${local.input}/soc-Epinions1.txt ${local.output}/variants ${k.trials}

# Task 2 (partner's DeGroot job): consumes the variant files produced by
# job1. Args here are a placeholder -- update to match the partner's
# actual CLI once finalized.
local-job2: jar
	spark-submit --class ${job2.class} --master local[*] ${jar.path} \
		${local.output}/variants ${local.output}/degroot-results

local-all: local-job1 local-job2

# Task 1, stage 2: connected-components stats for one variant (pick with
# VARIANT=..., defaults to "original"). Run once per variant you want
# stats for.
local-cc: jar
	rm -rf ${local.output}/cc-stats/${VARIANT}
	spark-submit --class ${job1b.class} --master local[*] \
		--conf spark.local.dir=${local.output}/spark-tmp \
		${jar.path} \
		${local.output}/variants/${VARIANT}.txt ${local.output}/cc-stats/${VARIANT} 20 ${local.output}/cc-checkpoint/${VARIANT}

# -------------------- AWS S3 --------------------

make-bucket:
	aws s3 mb s3://${aws.bucket.name} --region ${aws.region}

upload-input-aws: make-bucket
	aws s3 sync ${local.input} s3://${aws.bucket.name}/${aws.input}

upload-app-aws:
	aws s3 cp ${jar.path} s3://${aws.bucket.name}/

delete-output-aws:
	aws s3 rm s3://${aws.bucket.name}/ --recursive \
		--exclude "*" --include "${aws.output}*"

download-output-aws: clean-local-output
	mkdir -p ${local.output}
	aws s3 sync s3://${aws.bucket.name}/${aws.output} ${local.output}

# -------------------- EMR RUNS --------------------

aws-emr-job1: jar upload-input-aws upload-app-aws delete-output-aws
	aws emr create-cluster \
		--name "Job1-${job1.class}" \
		--release-label ${aws.emr.release} \
		--instance-groups \
			'[{"InstanceCount":${aws.emr.num.core.nodes},"InstanceGroupType":"CORE","InstanceType":"${aws.emr.instance.type}","EbsConfiguration":{"EbsBlockDeviceConfigs":[{"VolumeSpecification":{"VolumeType":"gp2","SizeInGB":64},"VolumesPerInstance":1}]}},{"InstanceCount":${aws.emr.num.master.nodes},"InstanceGroupType":"MASTER","InstanceType":"${aws.emr.instance.type}","EbsConfiguration":{"EbsBlockDeviceConfigs":[{"VolumeSpecification":{"VolumeType":"gp2","SizeInGB":64},"VolumesPerInstance":1}]}}]' \
		--applications Name=Spark \
		--steps \
			'[{"Name":"${job1.class}","Type":"CUSTOM_JAR","Jar":"command-runner.jar","ActionOnFailure":"TERMINATE_CLUSTER","Args":["spark-submit","--deploy-mode","cluster","--class","${job1.class}","s3://${aws.bucket.name}/${jar.name}","s3://${aws.bucket.name}/${aws.input}/soc-Epinions1.txt","s3://${aws.bucket.name}/${aws.output}/variants","${k.trials}"]}]' \
		--log-uri s3://${aws.bucket.name}/${aws.log.dir} \
		--service-role EMR_DefaultRole \
		--ec2-attributes InstanceProfile=EMR_EC2_DefaultRole,SubnetId=${aws.subnet.id} \
		--enable-debugging \
		--auto-terminate \
		--region ${aws.region}

# Placeholder args -- update once the partner's DeGroot CLI is finalized.
aws-emr-job2: jar upload-app-aws delete-output-aws
	aws emr create-cluster \
		--name "Job2-${job2.class}" \
		--release-label ${aws.emr.release} \
		--instance-groups \
			'[{"InstanceCount":${aws.emr.num.core.nodes},"InstanceGroupType":"CORE","InstanceType":"${aws.emr.instance.type}","EbsConfiguration":{"EbsBlockDeviceConfigs":[{"VolumeSpecification":{"VolumeType":"gp2","SizeInGB":64},"VolumesPerInstance":1}]}},{"InstanceCount":${aws.emr.num.master.nodes},"InstanceGroupType":"MASTER","InstanceType":"${aws.emr.instance.type}","EbsConfiguration":{"EbsBlockDeviceConfigs":[{"VolumeSpecification":{"VolumeType":"gp2","SizeInGB":64},"VolumesPerInstance":1}]}}]' \
		--applications Name=Spark \
		--steps \
			'[{"Name":"${job2.class}","Type":"CUSTOM_JAR","Jar":"command-runner.jar","ActionOnFailure":"TERMINATE_CLUSTER","Args":["spark-submit","--deploy-mode","cluster","--class","${job2.class}","s3://${aws.bucket.name}/${jar.name}","s3://${aws.bucket.name}/${aws.output}/variants","s3://${aws.bucket.name}/${aws.output}/degroot-results"]}]' \
		--log-uri s3://${aws.bucket.name}/${aws.log.dir} \
		--service-role EMR_DefaultRole \
		--ec2-attributes InstanceProfile=EMR_EC2_DefaultRole,SubnetId=${aws.subnet.id} \
		--enable-debugging \
		--auto-terminate \
		--region ${aws.region}

# Task 1, stage 2 on EMR: connected-components for one variant. This is
# the iterative job -- the natural one to use for the report's
# speedup (vary aws.emr.num.core.nodes) and scalability (vary VARIANT,
# e.g. original vs. a 25% sample) experiments.
# Assumes job1 has already been run on EMR so the variant files already
# exist under s3://${aws.bucket.name}/${aws.output}/variants/.
aws-emr-cc: jar upload-app-aws
	aws emr create-cluster \
		--name "CC-${VARIANT}-${aws.emr.num.core.nodes}nodes" \
		--release-label ${aws.emr.release} \
		--instance-groups \
			'[{"InstanceCount":${aws.emr.num.core.nodes},"InstanceGroupType":"CORE","InstanceType":"${aws.emr.instance.type}","EbsConfiguration":{"EbsBlockDeviceConfigs":[{"VolumeSpecification":{"VolumeType":"gp2","SizeInGB":64},"VolumesPerInstance":1}]}},{"InstanceCount":${aws.emr.num.master.nodes},"InstanceGroupType":"MASTER","InstanceType":"${aws.emr.instance.type}","EbsConfiguration":{"EbsBlockDeviceConfigs":[{"VolumeSpecification":{"VolumeType":"gp2","SizeInGB":64},"VolumesPerInstance":1}]}}]' \
		--applications Name=Spark \
		--steps \
			'[{"Name":"${job1b.class}-${VARIANT}","Type":"CUSTOM_JAR","Jar":"command-runner.jar","ActionOnFailure":"TERMINATE_CLUSTER","Args":["spark-submit","--deploy-mode","cluster","--class","${job1b.class}","s3://${aws.bucket.name}/${jar.name}","s3://${aws.bucket.name}/${aws.output}/variants/${VARIANT}.txt","s3://${aws.bucket.name}/${aws.output}/cc-stats/${VARIANT}","50","hdfs:///cc-checkpoint/${VARIANT}"]}]' \
		--log-uri s3://${aws.bucket.name}/${aws.log.dir} \
		--service-role EMR_DefaultRole \
		--ec2-attributes InstanceProfile=EMR_EC2_DefaultRole,SubnetId=${aws.subnet.id} \
		--enable-debugging \
		--auto-terminate \
		--region ${aws.region}

VARIANTS=original degree_removed_10pct community_removed_10pct random_removed_10pct_trial1 random_removed_10pct_trial2 random_removed_10pct_trial3 random_removed_10pct_trial4 random_removed_10pct_trial5

# Runs ConnectedComponents on ALL 8 variants as sequential steps on a
# single EMR cluster -- avoids paying cluster-boot overhead 8 separate
# times. ActionOnFailure=CONTINUE so one variant failing doesn't abort
# the rest. Each variant's stats land at their usual S3 path
# (output/cc-stats/<variant>/part-00000); fetch each individually with
# `make aws-emr-cc-fetch VARIANT=<name>` once the cluster terminates.
aws-emr-cc-all: jar upload-app-aws
	@mkdir -p ${local.output}
	@rm -f ${local.output}/emr-cc-all-steps.json
	@echo '[' > ${local.output}/emr-cc-all-steps.json
	@first=1; \
	for v in $(VARIANTS); do \
		if [ $$first -eq 0 ]; then echo ',' >> ${local.output}/emr-cc-all-steps.json; fi; \
		first=0; \
		echo '{"Name":"CC-'$$v'","Type":"CUSTOM_JAR","Jar":"command-runner.jar","ActionOnFailure":"CONTINUE","Args":["spark-submit","--deploy-mode","cluster","--class","${job1b.class}","s3://${aws.bucket.name}/${jar.name}","s3://${aws.bucket.name}/${aws.output}/variants/'$$v'.txt","s3://${aws.bucket.name}/${aws.output}/cc-stats/'$$v'","50","hdfs:///cc-checkpoint/'$$v'"]}' >> ${local.output}/emr-cc-all-steps.json; \
	done
	@echo ']' >> ${local.output}/emr-cc-all-steps.json
	aws emr create-cluster \
		--name "CC-all-variants-${aws.emr.num.core.nodes}nodes" \
		--release-label ${aws.emr.release} \
		--instance-groups \
			'[{"InstanceCount":${aws.emr.num.core.nodes},"InstanceGroupType":"CORE","InstanceType":"${aws.emr.instance.type}","EbsConfiguration":{"EbsBlockDeviceConfigs":[{"VolumeSpecification":{"VolumeType":"gp2","SizeInGB":64},"VolumesPerInstance":1}]}},{"InstanceCount":${aws.emr.num.master.nodes},"InstanceGroupType":"MASTER","InstanceType":"${aws.emr.instance.type}","EbsConfiguration":{"EbsBlockDeviceConfigs":[{"VolumeSpecification":{"VolumeType":"gp2","SizeInGB":64},"VolumesPerInstance":1}]}}]' \
		--applications Name=Spark \
		--steps file://${local.output}/emr-cc-all-steps.json \
		--log-uri s3://${aws.bucket.name}/${aws.log.dir} \
		--service-role EMR_DefaultRole \
		--ec2-attributes InstanceProfile=EMR_EC2_DefaultRole,SubnetId=${aws.subnet.id} \
		--enable-debugging \
		--auto-terminate \
		--region ${aws.region}

# Fetch all 8 variants' stats at once, after aws-emr-cc-all completes.
aws-emr-cc-all-fetch:
	@for v in $(VARIANTS); do \
		echo "=== $$v ==="; \
		$(MAKE) aws-emr-cc-fetch VARIANT=$$v; \
	done
# Run 1: small cluster. Output lands at cc-stats/original (2-node run).
aws-emr-cc-speedup-small:
	$(MAKE) aws-emr-cc VARIANT=original aws.emr.num.core.nodes=2

# Run 2: larger cluster (Makefile default is 4 -- override here if you
# want a different comparison point, e.g. aws.emr.num.core.nodes=8).
# NOTE: this overwrites cc-stats/original from the run above, so copy the
# small-cluster stats+timing out of S3/EMR console before running this.
aws-emr-cc-speedup-large:
	$(MAKE) aws-emr-cc VARIANT=original aws.emr.num.core.nodes=4

# ---- Report experiments: scalability (same cluster, 2 input sizes) ----
# Large input: full graph (508,837 edges).
aws-emr-cc-scalability-large:
	$(MAKE) aws-emr-cc VARIANT=original aws.emr.num.core.nodes=4

# Small input: degree_removed_10pct (46,171 edges -- 91% smaller).
aws-emr-cc-scalability-small:
	$(MAKE) aws-emr-cc VARIANT=degree_removed_10pct aws.emr.num.core.nodes=4

# List EMR steps (start/end times) for a cluster -- use this to read off
# each run's actual duration for the report's speedup/scalability tables.
aws-emr-list-steps:
	aws emr list-steps --cluster-id ${CLUSTER_ID} --region ${aws.region}

# Pull one variant's connected-components stats file down from S3.
aws-emr-cc-fetch:
	mkdir -p ${local.output}/cc-stats-aws/${VARIANT}
	aws s3 cp s3://${aws.bucket.name}/${aws.output}/cc-stats/${VARIANT}/ \
		${local.output}/cc-stats-aws/${VARIANT}/ --recursive
	cat ${local.output}/cc-stats-aws/${VARIANT}/part-00000

# -------------------- EC2 (standalone Spark cluster) --------------------
# Unlike EMR, this stands up a bare Spark standalone cluster directly on
# raw EC2 instances: one master + N workers, with Spark installed manually
# via user-data at boot. Useful if you want direct control over the Spark
# version/config, or to compare standalone-cluster performance against EMR.

# Launch the master node. Tags let the other targets find its IP later.
ec2-launch-master:
	aws ec2 run-instances \
		--image-id ${ec2.ami} \
		--count 1 \
		--instance-type ${ec2.instance.type} \
		--key-name ${ec2.key.name} \
		--security-group-ids ${ec2.security.group} \
		--tag-specifications 'ResourceType=instance,Tags=[{Key=Name,Value=${ec2.master.tag}}]' \
		--user-data file://ec2/install-spark.sh \
		--region ${aws.region}

# Launch N worker nodes.
ec2-launch-workers:
	aws ec2 run-instances \
		--image-id ${ec2.ami} \
		--count ${ec2.num.workers} \
		--instance-type ${ec2.instance.type} \
		--key-name ${ec2.key.name} \
		--security-group-ids ${ec2.security.group} \
		--tag-specifications 'ResourceType=instance,Tags=[{Key=Name,Value=${ec2.worker.tag}}]' \
		--user-data file://ec2/install-spark.sh \
		--region ${aws.region}

ec2-launch-cluster: ec2-launch-master ec2-launch-workers
	@echo "Cluster launching. Wait ~1-2 min for boot + Spark install, then run: make ec2-cluster-ips"

# Look up the running master/worker IPs (run after launch, once instances are 'running').
ec2-cluster-ips:
	@echo "Master:"
	@aws ec2 describe-instances --region ${aws.region} \
		--filters "Name=tag:Name,Values=${ec2.master.tag}" "Name=instance-state-name,Values=running" \
		--query "Reservations[].Instances[].[PublicIpAddress,PrivateIpAddress]" --output table
	@echo "Workers:"
	@aws ec2 describe-instances --region ${aws.region} \
		--filters "Name=tag:Name,Values=${ec2.worker.tag}" "Name=instance-state-name,Values=running" \
		--query "Reservations[].Instances[].[PublicIpAddress,PrivateIpAddress]" --output table

# Start the Spark master daemon on the master node (run once IPs are known;
# pass MASTER_IP=<public-ip> on the command line).
ec2-start-master:
	ssh -i ${ec2.key.path} ec2-user@${MASTER_IP} \
		"/opt/spark/sbin/start-master.sh"

# Attach a worker to the master (run once per worker IP; pass
# MASTER_PRIVATE_IP and WORKER_IP on the command line).
ec2-start-worker:
	ssh -i ${ec2.key.path} ec2-user@${WORKER_IP} \
		"/opt/spark/sbin/start-worker.sh spark://${MASTER_PRIVATE_IP}:7077"

# Copy the built jar and input data to the master node.
ec2-deploy: jar
	scp -i ${ec2.key.path} ${jar.path} ec2-user@${MASTER_IP}:~/
	scp -i ${ec2.key.path} -r ${local.input} ec2-user@${MASTER_IP}:~/

# Submit job1 to the standalone cluster (pass MASTER_IP and
# MASTER_PRIVATE_IP on the command line).
ec2-submit-job1:
	ssh -i ${ec2.key.path} ec2-user@${MASTER_IP} \
		"/opt/spark/bin/spark-submit --master spark://${MASTER_PRIVATE_IP}:7077 \
		--class ${job1.class} ~/${jar.name} \
		~/input/soc-Epinions1.txt ~/output/variants ${k.trials}"

# Submit job2 to the standalone cluster. Placeholder args -- update once
# the partner's DeGroot CLI is finalized.
ec2-submit-job2:
	ssh -i ${ec2.key.path} ec2-user@${MASTER_IP} \
		"/opt/spark/bin/spark-submit --master spark://${MASTER_PRIVATE_IP}:7077 \
		--class ${job2.class} ~/${jar.name} \
		~/output/variants ~/output/degroot-results"

# Pull results back from the master node.
ec2-fetch-output: clean-local-output
	mkdir -p ${local.output}
	scp -i ${ec2.key.path} -r ec2-user@${MASTER_IP}:~/output/* ${local.output}/

# Terminate all tagged instances (master + workers) to stop billing.
ec2-terminate-cluster:
	aws ec2 terminate-instances --region ${aws.region} --instance-ids \
		$(shell aws ec2 describe-instances --region ${aws.region} \
			--filters "Name=tag:Name,Values=${ec2.master.tag},${ec2.worker.tag}" "Name=instance-state-name,Values=running,pending,stopped" \
			--query "Reservations[].Instances[].InstanceId" --output text)