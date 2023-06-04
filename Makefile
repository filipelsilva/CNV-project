.PHONY: all workload javassist

all: workload javassist lbas

workload:
	(cd src/workload && mvn clean install)

javassist:
	(cd src/javassist && mvn clean install)

lbas:
	(cd src/lbas && mvn clean install)
