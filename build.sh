#!/bin/bash

SRC_DIR=src
LIB_DIR=lib
BUILD_DIR=build

if [ ! -d "$BUILD_DIR" ]; then
  mkdir "$BUILD_DIR"
fi

find "$SRC_DIR" -name "*.java" | xargs javac -d "$BUILD_DIR" -cp "$LIB_DIR/*:$SRC_DIR"


if [ $? -eq 0 ]; then
    echo "Compilation successful."
else
    echo "Compilation failed."
    exit 1
fi