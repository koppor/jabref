# -*- makefile -*-

.PHONY: all assemble build build-notest clean check test run tmp-clean checkstyle

## https://docs.gradle.org/current/userguide/build_environment.html#sec:gradle_configuration_properties
##	GRADLE_USER_HOME=/tmp/jabref-home
##                       default: ~/.gradle

# None of these helped in getting temporary files under /tmp/jabref
#
#	TMPDIR=/tmp/jabref
#	TEMP=/tmp/jabref
#	GRADLE_OPTS=-Djava.io.tmpdir=/tmp/jabref
#	JAVA_OPTS=-Djava.io.tmpdir=/tmp/jabref
#	JVM_OPTS=-Djava.io.tmpdir=/tmp/jabref
#	./gradlew -Djava.io.tmpdir=/tmp/jabref


GRADLE = ./gradlew


all: build

# https://devdocs.jabref.org/getting-into-the-code/guidelines-for-setting-up-a-local-workspace
#
# Generate additional source code: ./gradlew assemble
#
assemble:
	./gradlew  assemble
#

build:
	make tmp-clean
	make checkstyle
	$(GRADLE) build
	make tmp-clean
#

build-notest:
	make checkstyle
	make tmp-clean
	$(GRADLE)  build -x test
	make tmp-clean
#

clean:
	$(GRADLE) clean
	make tmp-clean
#

check:
	make tmp-clean
	make checkstyle
	$(GRADLE) check
	make tmp-clean
#

test:
	make tmp-clean
	make checkstyle
	$(GRADLE) test
	make tmp-clean
#

run:
	make tmp-clean
	make checkstyle
	$(GRADLE) run
	make tmp-clean
#


tmp-clean:

old-tmp-clean:
	rm -rf /tmp/journal[0-9][0-9][0-9][0-9][0-9][0-9]* 	\
	       /tmp/junit[0-9][0-9][0-9][0-9][0-9][0-9]* 	\
	       /tmp/gradle-worker-*               		\
	       /tmp/LICENSE*.md 				\
	       /tmp/INPROC-2016*.pdf				\
	       /tmp/[0-9][0-9][0-9][0-9][0-9][0-9][0-9]*.tmp
#

checkstyle:
	./gradlew checkstyleMain 2>&1 | sed -e 's|[[]ant[:]checkstyle] [[]ERROR] ||g'
	./gradlew checkstyletest 2>&1 | sed -e 's|[[]ant[:]checkstyle] [[]ERROR] ||g'
#

pmd:
	pmd -d src/main/java/org/jabref/model/openoffice
	pmd -d src/main/java/org/jabref/logic/openoffice
	pmd -d src/main/java/org/jabref/gui/openoffice
