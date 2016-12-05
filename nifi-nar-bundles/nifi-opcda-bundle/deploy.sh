#!/bin/bash
OPCDA_VERSION=1.0.0-RC3
export NIFI_HOME=/usr/local/nifi
export JAVA_HOME=/usr

mvn -DskipTests clean package
$NIFI_HOME/bin/nifi.sh stop
rm $NIFI_HOME/lib/nifi-opcda-nar-$OPCDA_VERSION.nar 
cp nifi-opcda-nar/target/nifi-opcda-nar-$OPCDA_VERSION.nar $NIFI_HOME/lib
$NIFI_HOME/bin/nifi.sh start
tail -f $NIFI_HOME/logs/nifi-app.log &
