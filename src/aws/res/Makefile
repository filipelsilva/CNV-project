.PHONY: all lwt mcpu clean

all: EC2LaunchWaitTerminate.java EC2MeasureCPU.java
	javac -cp ../../../../aws-java-sdk-1.12.468/lib/aws-java-sdk-1.12.468.jar:../../../../aws-java-sdk-1.12.468/third-party/lib/*:. EC2LaunchWaitTerminate.java
	javac -cp ../../../../aws-java-sdk-1.12.468/lib/aws-java-sdk-1.12.468.jar:../../../../aws-java-sdk-1.12.468/third-party/lib/*:. EC2MeasureCPU.java

lwt: EC2LaunchWaitTerminate.class
	source ../scripts/config.sh && java -cp ../../../../aws-java-sdk-1.12.468/lib/aws-java-sdk-1.12.468.jar:../../../../aws-java-sdk-1.12.468/third-party/lib/*:. EC2LaunchWaitTerminate

mcpu: EC2MeasureCPU.java
	source ../scripts/config.sh && java -cp ../../../../aws-java-sdk-1.12.468/lib/aws-java-sdk-1.12.468.jar:../../../../aws-java-sdk-1.12.468/third-party/lib/*:. EC2MeasureCPU

clean: *.class
	rm -rf *.class
