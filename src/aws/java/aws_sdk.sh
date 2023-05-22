#!/usr/bin/env bash

if [[ ! -f aws-java-sdk.zip ]]; then
	echo "Downloading aws-java-sdk.zip"
	curl "http://sdk-for-java.amazonwebservices.com/latest/aws-java-sdk.zip" -o "aws-java-sdk.zip"
else
	echo "aws-java-sdk.zip already exists"
fi

if [[ ! -d aws-java-sdk-$1 ]]; then
	echo "Unpacking aws-java-sdk-$1.zip"
	unzip "aws-java-sdk-$1.zip"
else
	echo "aws-java-sdk-$1 already exists"
fi
