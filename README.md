# JabRef Debianized

This branch is used to prepare a clean version for distribution of JabRef at Debian.
See https://tracker.debian.org/pkg/jabref for the current status of Debian at JabRef.

Full information of JabRef is available at https://github.com/JabRef/jabref

This branch is intended to be rebased onto the latest releases of JabRef to ease integration of the latest JabRef versions in Debian.

## Changes in comparison to master branch

* rewrite REAMDE.md to list the differences to the master branch
* adapt build.gradle: Only the plugins required for building remain
  * also remove install4j dependency
* remove library AppleJavaExtension.jar not required for linux
* remove MacAdapter.java
* adapt JabRefFrame.java not to use MacAdapter
* adapt circle.yml accordingly
* adapt external-libraries.txt accordingly
