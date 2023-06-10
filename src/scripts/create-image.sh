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

# Step 1: launch a vm instance.
$DIR/launch-vm.sh

# Step 2: install software in the VM instance.
$DIR/install-vm.sh "$1"

# Step 3: test VM instance.
$DIR/test-vm.sh

# Step 4: create VM image (AIM).
if [ "$1" = "webserver" ]; then
	aws ec2 create-image --instance-id $(cat instance.id) --name CNV-Webserver | jq -r .ImageId > image.id
else
	aws ec2 create-image --instance-id $(cat instance.id) --name CNV-LBAS | jq -r .ImageId > image.id
fi
echo "New VM image with id $(cat image.id)."

# Step 5: Wait for image to become available.
echo "Waiting for image to be ready... (this can take a couple of minutes)"
if [ "$1" = "webserver" ]; then
	aws ec2 wait image-available --filters Name=name,Values=CNV-Webserver
else
	aws ec2 wait image-available --filters Name=name,Values=CNV-LBAS
fi
echo "Waiting for image to be ready... done! \o/"

# Step 6: terminate the vm instance.
aws ec2 terminate-instances --instance-ids $(cat instance.id)
