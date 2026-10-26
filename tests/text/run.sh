#!/bin/sh
set -e
cd "$(dirname "$0")/../.."
rm -rf tests/text/build
mkdir -p tests/text/build
"${JAVAC:-javac}" -source 1.3 -target 1.3 -nowarn -d tests/text/build \
    jar/src/de/aatokombi/DisplayText.java \
    jar/src/de/aatokombi/TextScroller.java \
    tests/text/DisplayTextTest.java
exec "${JAVA:-java}" -cp tests/text/build DisplayTextTest
