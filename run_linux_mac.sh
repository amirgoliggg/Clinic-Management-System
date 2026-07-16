#!/bin/bash
mkdir -p bin
javac -d bin src/main/*.java src/models/*.java src/system/*.java src/enums/*.java
java -cp bin main.Main