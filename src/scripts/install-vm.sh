#!/usr/bin/env bash

# check number of arguments. if different that 1, abort
if [ "$#" -ne 1 ]; then
	echo "Usage: $0 [webserver|lbas]"
	exit 1
fi

# check if argument is valid
if [ "$1" != "webserver" ] && [ "$1" != "lbas" ]; then
	echo "Usage: $0 [webserver|lbas]"
	exit 1
fi

source config.sh

# Install java.
cmd="sudo yum update -y && sudo yum install java-11-amazon-corretto.x86_64 -y"
ssh -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAIR_PATH ec2-user@$(cat instance.dns) $cmd

# Send config file to instance.
scp -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAIR_PATH $DIR/../scripts/config.sh ec2-user@$(cat instance.dns):

# Send jars to instance.
if [ "$1" = "webserver" ]; then
	scp -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAIR_PATH $DIR/../javassist/target/JavassistWrapper-1.0-jar-with-dependencies.jar ec2-user@$(cat instance.dns):
	scp -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAIR_PATH $DIR/../webserver/webserver/target/webserver-1.0.0-SNAPSHOT-jar-with-dependencies.jar ec2-user@$(cat instance.dns):
else
	scp -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAIR_PATH $DIR/../lbas/target/LBAS-1.0-jar-with-dependencies.jar ec2-user@$(cat instance.dns):
fi

# Setup the webserver to start at boot.
if [ "$1" = "webserver" ]; then
	java_cmd="cd /home/ec2-user && source config.sh && java -cp webserver-1.0.0-SNAPSHOT-jar-with-dependencies.jar -Xbootclasspath/a:/JavassistWrapper-1.0-jar-with-dependencies.jar -javaagent:JavassistWrapper-1.0-jar-with-dependencies.jar=ICount:pt.ulisboa.tecnico.cnv,javax.imageio:output pt.ulisboa.tecnico.cnv.webserver.WebServer"
else
	java_cmd="cd /home/ec2-user && source config.sh && java -cp LBAS-1.0-jar-with-dependencies.jar pt.ulisboa.tecnico.cnv.lbas.LBAS"
fi
cmd="echo \"$java_cmd\" | sudo tee -a /etc/rc.local && sudo chmod +x /etc/rc.local"
ssh -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAIR_PATH ec2-user@$(cat instance.dns) $cmd
