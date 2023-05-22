.PHONY: all lwt mcpu clean

VERSION=1.12.472
FLAGS=-cp aws-java-sdk-${VERSION}/lib/aws-java-sdk-${VERSION}.jar:aws-java-sdk-${VERSION}/third-party/lib/*:.
INITIALIZE=./aws_sdk.sh ${VERSION} && source ../bash/config.sh &&

all: EC2LaunchWaitTerminate.java EC2MeasureCPU.java
	${INITIALIZE} javac ${FLAGS} EC2LaunchWaitTerminate.java
	${INITIALIZE} javac ${FLAGS} EC2MeasureCPU.java

lwt: EC2LaunchWaitTerminate.class
	${INITIALIZE} && java ${FLAGS} EC2LaunchWaitTerminate

mcpu: EC2MeasureCPU.java
	${INITIALIZE} && java ${FLAGS} EC2MeasureCPU

clean: *.class
	rm -rf *.class
