#!/usr/bin/env bash

source config.sh

aws lambda delete-function --function-name CNV-ImageCompression
aws lambda delete-function --function-name CNV-FoxesAndRabbits
aws lambda delete-function --function-name CNV-InsectWar

aws iam detach-role-policy \
        --role-name lambda-role \
        --policy-arn arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole

aws iam delete-role --role-name lambda-role
