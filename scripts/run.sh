#!/usr/bin/bash
LIB=target/muse-war-1.0.0-SNAPSHOT/WEB-INF/lib;
CP="";
for lib in $LIB/*.jar;
do
    CP=$CP:$lib;
done
CP=$CP:target/muse-war-1.0.0-SNAPSHOT/WEB-INF/classes/;
java -Xmx4g -cp $CP edu.stanford.muse.ner.model.SequenceModel