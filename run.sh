#!/bin/bash

TERMINAL=alacritty
DOWNLOADERS_NUM=1
BARRELS_NUM=1
CLIENTS_NUM=1

JAVA_RUN="java -cp build:lib/*"

GATEWAY_CLASS="Gateway"
DOWNLOADER_CLASS="Downloader"
BARREL_CLASS="IndexStorageBarrel"
CLIENT_CLASS="Client"

$TERMINAL -e $JAVA_RUN $GATEWAY_CLASS &

sleep 1

for ((i=1; i<=DOWNLOADERS_NUM; i++))
do
  $TERMINAL -e $JAVA_RUN $DOWNLOADER_CLASS &
done

for ((i=1; i<=BARRELS_NUM; i++))
do
  $TERMINAL -e $JAVA_RUN $BARREL_CLASS &
done

for ((i=1; i<=CLIENTS_NUM; i++))
do
  $TERMINAL -e $JAVA_RUN $CLIENT_CLASS &
done
