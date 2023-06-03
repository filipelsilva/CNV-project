#!/usr/bin/env bash

source config.sh

# Install java.
cmd="sudo yum update -y && sudo yum install java-11-amazon-corretto.x86_64 -y"
ssh -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAIR_PATH ec2-user@$(cat instance.dns) $cmd

# Send jars to instance.
scp -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAIR_PATH $DIR/../javassist/target/JavassistWrapper-1.0-jar-with-dependencies.jar ec2-user@$(cat instance.dns):
scp -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAIR_PATH $DIR/../workload/webserver/target/webserver-1.0.0-SNAPSHOT-jar-with-dependencies.jar ec2-user@$(cat instance.dns):

# Setup the webserver to start at boot.
java_cmd="cd /home/ec2-user && java -cp webserver-1.0.0-SNAPSHOT-jar-with-dependencies.jar -Xbootclasspath/a:JavassistWrapper-1.0-jar-with-dependencies.jar -javaagent:JavassistWrapper-1.0-jar-with-dependencies.jar=ICount:pt.ulisboa.tecnico.cnv,javax.imageio:output pt.ulisboa.tecnico.cnv.webserver.WebServer"
cmd="echo \"$java_cmd\" | sudo tee -a /etc/rc.local && sudo chmod +x /etc/rc.local"
ssh -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAIR_PATH ec2-user@$(cat instance.dns) $cmd
