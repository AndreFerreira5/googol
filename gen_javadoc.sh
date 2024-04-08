#!/bin/bash

DOC_DIR=doc
OVERVIEW_FILE=report.html

if [ ! -d "$DOC_DIR" ]; then
  mkdir "$DOC_DIR"
fi

if [ ! -f "$OVERVIEW_FILE" ]; then
  echo "Overview file $OVERVIEW_FILE not found. Proceeding with no overview flag..."
  find src -name "*.java" | xargs javadoc -private -d doc -classpath 'lib/*'
else
  find src -name "*.java" | xargs javadoc -private -d doc -classpath 'lib/*' -overview "$OVERVIEW_FILE"
fi

if [ $? -eq 0 ]; then
  echo "Compilation successful."
else
  echo "Compilation failed."
  exit 1
fi
