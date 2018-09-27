#!/bin/bash
set -x
set -e
set -u

mvn package -Dmaven.test.skip=true
mvn clean
mvn package -PimageTests

