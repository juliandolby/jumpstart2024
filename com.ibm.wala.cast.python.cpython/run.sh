#!/bin/bash  
  
ME=`realpath $0`  
DIR=`dirname $ME`  
  
$JAVA_HOME/bin/java -Xdebug -Xrunjdwp:transport=dt_socket,address=127.0.0.1:6660,server=y,suspend=y -Djava.library.path=/Users/paulscoropan/.local/pipx/venvs/jep/lib/python3.13/site-packages/jep/ -jar $DIR/target/com.ibm.wala.cast.python.cpython-0.0.1-SNAPSHOT-shaded.jar /Users/paulscoropan/dev/chester-train/dataset.py
