#!/usr/bin/env bash

source config.sh

aws lambda delete-function --function-name CNV-test

aws iam detach-role-policy \
        --role-name lambda-role \
        --policy-arn arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole

aws iam delete-role --role-name lambda-role
