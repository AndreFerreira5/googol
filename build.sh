#!/bin/bash

SRC_DIR=src
LIB_DIR=lib
BUILD_DIR=build
JAR_DIR=jar

if [ ! -d "$BUILD_DIR" ]; then
  mkdir "$BUILD_DIR"
fi

if [ ! -d "$JAR_DIR" ]; then
    mkdir "$JAR_DIR"
fi

find "$SRC_DIR" -name "*.java" | xargs javac -d "$BUILD_DIR" -cp "$LIB_DIR/*:$SRC_DIR"


if [ $? -eq 0 ]; then
    echo "Compilation successful."
else
    echo "Compilation failed."
    exit 1
fi

jar -cvfe "$JAR_DIR/Gateway.jar" Gateway -C "$BUILD_DIR" .
jar -cvfe "$JAR_DIR/Client.jar" Client -C "$BUILD_DIR" .
jar -cvfe "$JAR_DIR/IndexStorageBarrel.jar" IndexStorageBarrel -C "$BUILD_DIR" .
jar -cvfe "$JAR_DIR/Downloader.jar" Downloader -C "$BUILD_DIR" .

echo "JAR files created successfully."
