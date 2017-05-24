#!/usr/bin/bash
LIB=target/muse-1.0.0-SNAPSHOT/WEB-INF/lib;
CP="";
for lib in $LIB/*.jar;
do
    CP=$CP:$lib;
done
for lib in lib/*.jar;
do
    CP=$CP:$lib;
done
CP=$CP:target/muse-1.0.0-SNAPSHOT/WEB-INF/classes/;
echo $CP;
# set to the path where lijscip.so file exists
export LD_LIBRARY_PATH=/home/vihari/repos/JSCIPOpt/build
java -Djava.library.path=/home/vihari/repos/JSCIPOpt/build -Xmx10g -cp $CP edu.stanford.muse.ner.model.ARIModel
