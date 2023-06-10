.PHONY: all webserver javassist

all: webserver javassist lbas

webserver:
	(cd src/webserver && mvn clean install)

javassist:
	(cd src/javassist && mvn clean install)

lbas:
	(cd src/lbas && mvn clean install)
