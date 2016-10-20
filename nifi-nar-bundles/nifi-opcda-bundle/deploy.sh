#!/bin/bash
export NIFI_HOME=/usr/local/nifi

mvn -DskipTests clean package
$NIFI_HOME/bin/nifi.sh stop
rm $NIFI_HOME/lib/nifi-opcdaclient-nar-1.0-SNAPSHOT.nar 
cp nifi-opcda-nar/target/nifi-opcda-nar-1.0-SNAPSHOT.nar $NIFI_HOME/lib
$NIFI_HOME/bin/nifi.sh start
tail -f $NIFI_HOME/logs/nifi-app.log &
