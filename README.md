# Googol

## Build
This project was created and developed using **OpenJDK 17.0.10**, so that's the recommended one

### Backend
```shell
./gradlew build
./gradlew pack
```

### Frontend
```shell
./gradlew bootJar
```

## Usage
### Backend
```shell
java -jar Gateway-0.1.0.jar
java -jar IndexStorageBarrel-0.1.0.jar
java -jar Downloader-0.1.0.jar
java -jar Client-0.1.0.jar
```

### Frontend
```shell
java -jar frontend-0.1.0.jar
```