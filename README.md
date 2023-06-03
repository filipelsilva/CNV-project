# CNV Project, 2023

## Steps to run

1. Create AWS keypair, and download the `.pem` file to the root folder of this
project. Name it `awskeypair.pem`.

2. Run `make` on the root folder (where this README is).

3. `cd src/scripts/bash`

3.1. To create the image, `./create-image.sh`

3.2. To launch the whole deployment, `./launch-deployment.sh`

3.3. To destroy the whole deployment, `./terminate-deployment.sh`
