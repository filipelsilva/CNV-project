# CNV Project, 2023

## Steps to run

1. Create an AWS keypair, and download the `.pem` file to the root folder of
this project. Name it `awskeypair.pem`.

2. Create a copy of file `config.sh.example` and name it `config.sh`. Fill in
the `<CHANGE_ME>` fields with the appropriate values. This includes creating a
security group.

4. Run `make` on the root folder (where this README is).

5. `cd src/scripts`

6. To create the images, `./create-image.sh webserver` and `./create-image.sh
lbas`. To avoid errors, please create the *webserver* image first.

7. To launch the whole deployment, `./launch-vm.sh CNV-LBAS`
