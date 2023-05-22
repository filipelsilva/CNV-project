#!/usr/bin/env bash

aws ec2 deregister-image --image-id $(cat image.id)
