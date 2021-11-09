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


PMD = pmd -f text -R ../tools/pmd/pmd-java-rules.xml -cache /tmp/pmd-jabref-cache

pmd:
#	$(PMD) -d src/main/java/org/jabref/model/openoffice
#	$(PMD) -d src/main/java/org/jabref/logic/openoffice
#
#
# - Ignore problems in old files (OOBibBase, Bootstrap, DetectOpenOfficeInstallation).
# - Ignore UnusedPrivateMethod for @FXML initalize / addStyleFile
#
# - Ignore IdenticalCatchBranches : PMD seems to ignore that textually
#   identical branches can lead to different code based on type of the
#   exception.
#
# - Ignore some warnings in OOBibStyle.java for old stuff to be removed
#
	$(PMD) -d src/main/java/org/jabref/logic/openoffice \
	| egrep -v 'src/main/java/org/jabref/gui/openoffice/OOBibBase.java' \
	| egrep -v 'src/main/java/org/jabref/gui/openoffice/Bootstrap.java' \
	| egrep -v 'src/main/java/org/jabref/gui/openoffice/DetectOpenOfficeInstallation.java' \
	| egrep -v 'src/main/java/org/jabref/gui/openoffice/ManageCitationsDialogView.java' \
	| egrep -v 'src/main/java/org/jabref/gui/openoffice/OpenOfficePanel.java' \
	| egrep -v 'src/main/java/org/jabref/logic/openoffice/OpenOfficePreferences.java' \
	| egrep -v 'src/main/java/org/jabref/logic/openoffice/style/OOPreFormatter.java' \
	| egrep -v "UnusedPrivateMethod.*initialize" \
	| egrep -v 'UnusedPrivateMethod:.*addStyleFile' \
	| egrep -v '\sIdenticalCatchBranches:\s' \
	| egrep -v '\sPreserveStackTrace:\s' \
	| egrep -v '\sUseUtilityClass:\s' \
	| egrep -v 'ShortVariable:	Avoid variables with short names like (a|b|db|aa|bb)$$' \
	| egrep -v 'model/openoffice/style/CitedKeys.java:.*LooseCoupling:.*Avoid.*LinkedHashMap' \
	| egrep -v 'OOBibStyle.java.*names like (i1|al|to|j)$$' \
	| egrep -v 'OOBibStyle.java.*UseVarargs:' \
	| egrep -v 'OOBibStyleGetCitationMarker.java.*PrematureDeclaration:' \
	| egrep -v 'OOBibStyleGetCitationMarker.java.*SimplifiedTernary:' \
	| egrep -v 'OOBibStyleGetCitationMarker.java.*ShortVariable:.*like (j)$$' \
	| egrep -v 'OOBibStyleGetNumCitationMarker.java.*ShortVariable:.*like (na|nb)$$' \
	| egrep -v 'OOBibStyleGetNumCitationMarker.java.*(PrematureDeclaration|EmptyIfStmt):' \
	| egrep -v 'NamedRangeReferenceMark.java.*PrematureDeclaration:' \
	| egrep -v 'OOFormatBibliography.java.*(EmptyIfStmt|PrematureDeclaration):'


