#!/usr/bin/env bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
export DIR

export AWS_DEFAULT_REGION=us-east-1
export AWS_ACCOUNT_ID=852177647033
export AWS_ACCESS_KEY_ID=put-something-here
export AWS_SECRET_ACCESS_KEY=put-something-here
export AWS_EC2_SSH_KEYPAR_PATH=../../../vm/cnv-shared/Lab02/awskeypair.pem
export AWS_SECURITY_GROUP=CNV-ssh+http
export AWS_KEYPAIR_NAME=awskeypair
