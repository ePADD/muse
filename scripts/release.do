#!/bin/csh -fe
set build_info = "Built by `whoami` on `hostname`, `date '+%a %d %b %Y %l:%M %p'` git branch: `git rev-parse --abbrev-ref HEAD` latest commit id: `git rev-parse HEAD` working dir: `pwd`"
java -version
echo $build_info
set version = `cat version`
echo 'Muse version '${version}  
echo 'Muse version '${version}  >! WebContent/version.jsp
echo 'package edu.stanford.muse.util;' >! src/java/edu/stanford/muse/util/Version.java
echo 'public class Version {  public static final String version = "'${version}'"; ' >> src/java/edu/stanford/muse/util/Version.java
echo 'public static String appName = "muse";' >> src/java/edu/stanford/muse/util/Version.java
echo 'public static String buildInfo = "'${build_info}'";} ' >> src/java/edu/stanford/muse/util/Version.java

mvn 
