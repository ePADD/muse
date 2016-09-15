#!/usr/bin/bash
LIB=target/muse-1.0.0-SNAPSHOT/WEB-INF/lib;
CP="";
for lib in $LIB/*.jar;
do
    CP=$CP:$lib;
done
CP=$CP:target/muse-1.0.0-SNAPSHOT/WEB-INF/classes/;
java -Xmx10g -cp $CP edu.stanford.muse.ner.model.SequenceModel
