#!/usr/bin/env bash

source config.sh

aws iam create-role \
        --role-name lambda-role \
        --assume-role-policy-document '{"Version": "2012-10-17","Statement": [{ "Effect": "Allow", "Principal": {"Service": "lambda.amazonaws.com"}, "Action": "sts:AssumeRole"}]}'

aws iam attach-role-policy \
        --role-name lambda-role \
        --policy-arn arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole

aws lambda create-function \
        --function-name CNV-ImageCompression \
        --zip-file fileb://$DIR/../webserver/webserver/target/webserver-1.0.0-SNAPSHOT-jar-with-dependencies.jar \
        --handler pt.ulisboa.tecnico.cnv.compression.BaseCompressingHandler::handleRequest \
        --runtime java11 \
        --timeout 5 \
        --memory-size 256 \
        --role arn:aws:iam::$AWS_ACCOUNT_ID:role/lambda-role

aws lambda create-function \
        --function-name CNV-FoxesAndRabbits \
        --zip-file fileb://$DIR/../webserver/webserver/target/webserver-1.0.0-SNAPSHOT-jar-with-dependencies.jar \
        --handler pt.ulisboa.tecnico.cnv.foxrabbit.SimulationHandler::handleRequest \
        --runtime java11 \
        --timeout 5 \
        --memory-size 256 \
        --role arn:aws:iam::$AWS_ACCOUNT_ID:role/lambda-role

aws lambda create-function \
        --function-name CNV-InsectWars \
        --zip-file fileb://$DIR/../webserver/webserver/target/webserver-1.0.0-SNAPSHOT-jar-with-dependencies.jar \
        --handler pt.ulisboa.tecnico.cnv.insectwar.WarSimulationHandler::handleRequest \
        --runtime java11 \
        --timeout 5 \
        --memory-size 256 \
        --role arn:aws:iam::$AWS_ACCOUNT_ID:role/lambda-role
