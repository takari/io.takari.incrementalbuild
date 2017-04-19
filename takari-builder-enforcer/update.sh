#!/bin/bash

set -e

version=0.13.0

mvn versions:set -DnewVersion=${version} -DgenerateBackupPoms=false
mvn clean package

repo=/Users/rex.hoffman/git/blt/app/main/core/build/maven-plugin-repository/com/salesforce/maven/sandboxing

mkdir -p ${repo}/exec-maven-plugin/${version}
cp -f exec-maven-plugin/target/exec-maven-plugin-${version}.jar ${repo}/exec-maven-plugin/${version}/exec-maven-plugin-${version}.jar
cp -f exec-maven-plugin/pom.xml ${repo}/exec-maven-plugin/${version}/exec-maven-plugin-${version}.pom
p4 add ${repo}/exec-maven-plugin/${version}/exec-maven-plugin-${version}.jar
p4 add ${repo}/exec-maven-plugin/${version}/exec-maven-plugin-${version}.pom

mkdir -p ${repo}/modularity-enforcer/${version}
cp pom.xml ${repo}/modularity-enforcer/${version}/modularity-enforcer-${version}.pom
p4 add ${repo}/modularity-enforcer/${version}/modularity-enforcer-${version}.pom
rm -rf /Users/rex.hoffman/git/blt/app/main/core/build/repository/com/salesforce/maven/sandboxing/

mvn versions:set -DnewVersion=${version}-SNAPSHOT -DgenerateBackupPoms=false
