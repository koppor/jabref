# -*- makefile -*-

.PHONY: all assemble build build-notest clean check test run tmp-clean checkstyle

all: build

# https://devdocs.jabref.org/getting-into-the-code/guidelines-for-setting-up-a-local-workspace
#
# Generate additional source code: ./gradlew assemble
#
assemble:
	./gradlew  assemble

build:
	make checkstyle
	make tmp-clean
	./gradlew build
	make tmp-clean

build-notest:
	make checkstyle
	make tmp-clean
	./gradlew build -x test
	make tmp-clean

clean:
	./gradlew clean
	make tmp-clean

check:
	make checkstyle
	make tmp-clean
	./gradlew check

test:
	make checkstyle
	make tmp-clean
	./gradlew test

run:
	make checkstyle
	./gradlew run

tmp-clean:
	rm -rf /tmp/journal[0-9][0-9][0-9][0-9][0-9][0-9]* 	\
	       /tmp/junit[0-9][0-9][0-9][0-9][0-9][0-9]* 	\
	       /tmp/gradle-worker-*               		\
	       /tmp/LICENSE*.md 				\
	       /tmp/INPROC-2016*.pdf				\
	       /tmp/[0-9][0-9][0-9][0-9][0-9][0-9][0-9]*.tmp

checkstyle:
	./gradlew checkstyleMain 2>&1 | sed -e 's|[[]ant[:]checkstyle] [[]ERROR] ||g'
	./gradlew checkstyletest 2>&1 | sed -e 's|[[]ant[:]checkstyle] [[]ERROR] ||g'
