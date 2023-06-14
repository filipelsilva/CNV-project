# CNV Project, 2023

## Steps to run

1. Create an AWS keypair, and download the `.pem` file to the root folder of
this project. Name it `awskeypair.pem`.

2. Create a copy of file `config.sh.example` and name it `config.sh`. Fill in
the `<CHANGE_ME>` fields with the appropriate values. This includes creating a
security group.

3. Run `make` on the root folder (where this README is).

4. `cd src/scripts`

5. To create the images, `./create-image.sh webserver` and `./create-image.sh
lbas`. To avoid errors, please create the *webserver* image first. The *lbas*
image might create an extra instance, you can delete it if you want, or keep it
for further usage in the deployment.

6. To create the lambdas to use in the deployment, `./create-lambda.sh`.
`./destroy-lambda.sh` will delete them.

7. To launch the whole deployment, `./launch-vm.sh CNV-LBAS`

# TODO

[ ] Image bug in javassist/javax.io
[ ] Lambda error with image
