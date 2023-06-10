#!/usr/bin/env bash

source config.sh

if [ "$#" -ne 1 ]; then
	AMI_ID="resolve:ssm:/aws/service/ami-amazon-linux-latest/amzn2-ami-hvm-x86_64-gp2"
else
	AMI_ID=$(aws ec2 describe-images --filters "Name=name,Values=$1" --query "Images[0].ImageId" --output text)
fi

echo "Launching new instance with AMI $AMI_ID ..."

# Run new instance.
aws ec2 run-instances \
	--image-id "$AMI_ID" \
	--instance-type t2.micro \
	--key-name $AWS_KEYPAIR_NAME \
	--security-group-ids $AWS_SECURITY_GROUP \
	--monitoring Enabled=true | jq -r ".Instances[0].InstanceId" > instance.id
echo "New instance with id $(cat instance.id)."

# Wait for instance to be running.
aws ec2 wait instance-running --instance-ids $(cat instance.id)
echo "New instance with id $(cat instance.id) is now running."

# Extract DNS nane.
aws ec2 describe-instances \
	--instance-ids $(cat instance.id) | jq -r ".Reservations[0].Instances[0].NetworkInterfaces[0].PrivateIpAddresses[0].Association.PublicDnsName" > instance.dns
echo "New instance with id $(cat instance.id) has address $(cat instance.dns)."

# Wait for instance to have SSH ready.
while ! nc -z $(cat instance.dns) 22; do
	echo "Waiting for $(cat instance.dns):22 (SSH)..."
	sleep 0.5
done
echo "New instance with id $(cat instance.id) is ready for SSH access."
