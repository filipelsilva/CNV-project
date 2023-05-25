.PHONY: all workload javassist

all: workload javassist

workload:
	(cd src/workload && mvn clean install)

javassist:
	(cd src/javassist && mvn clean install)
