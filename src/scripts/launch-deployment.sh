#!/usr/bin/env bash

source config.sh

# Step 1: Create images
$DIR/create-image.sh webserver
$DIR/create-image.sh lbas

# Step 2: Create lambdas
$DIR/create-lambda.sh

# Step 3: Launch the LBAS instance
$DIR/launch-vm.sh CNV-LBAS
