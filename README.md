# CNV Project, 2023

## Steps to run

1. Create AWS keypair, and download the `.pem` file to the root folder of this
project. Name it `awskeypair.pem`.

2. Run `make` on the root folder (where this README is).

3. `cd src/scripts`

3.0. create a copy of file `config.sh.example` and name it `config.sh`. Fill in the `<CHANGE_ME>` fields with the appropriate values.

3.1. To create the images, `./create-image.sh webserver && ./create-image.sh
lbas`

3.2. To launch the whole deployment, `./launch-vm.sh CNV-LBAS`
