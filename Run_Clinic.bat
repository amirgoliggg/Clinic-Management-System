@echo off
javac -d bin src/enums/*.java src/models/*.java src/system/*.java src/main/*.java
java -cp bin main.Main
pause