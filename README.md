# CNV Project, 2023

## Steps to run

1. Create AWS keypair, point to it in the file `src/aws/bash/config.sh`.

2. Run `make` on the root folder (where this README is).

3. `cd src/aws/bash`

3.1. To create the image, `./create-image.sh`

3.2. To launch the whole deployment, `./launch-deployment.sh`

3.3. To destroy the whole deployment, `./terminate-deployment.sh`
