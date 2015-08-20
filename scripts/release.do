#!/bin/csh -fe
set build_info = "Built by `whoami`, `date '+%a %d %b %Y %l:%M %p'`"
java -version
echo $build_info
set version = `cat version`
echo 'ePADD version '${version}  
echo 'ePADD version '${version}  >! WebContent/version.jsp
echo 'package edu.stanford.muse;' >! src/java/edu/stanford/muse/Version.java
echo 'public class Version {  public static final String version = "'${version}'"; ' >> src/java/edu/stanford/muse/Version.java
echo 'public static final String appName = "muse";' >> src/java/edu/stanford/muse/Version.java
echo 'public static final String buildInfo = "'${build_info}'";} ' >> src/java/edu/stanford/muse/Version.java

cd ../muse
mvn -f pom-common.xml
mvn -f pom-webapp.xml
# now we'll have a muse-1.0.0.... .war available
