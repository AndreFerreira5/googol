# Googol

## Build
This project was created and developed using **OpenJDK 17.0.10**, so that's the recommended one

**Using build script**
```shell
./build.sh
```
**Manually**
```sh
mkdir build
javac -d build -cp "lib/*" src/*.java
```

## Usage

**Using run script**
Config the script to your own liking, by default it spawns only one instance of the gateway, downloader, barrel and client.
```shell
./run.sh
```
**Manually**
*For each class:*
```sh
java -cp "build:lib/*" class.java
```
