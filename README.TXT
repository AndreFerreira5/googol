COMPILAR BACKEND
./gradlew build
./gradlew pack

COMPILAR FRONTEND
./gradlew bootJar

CORRER BACKEND (ficheiros .jar compilados em backend/build/libs)
java -jar Gateway-0.1.0.jar
java -jar IndexStorageBarrel-0.1.0.jar
java -jar Downloader-0.1.0.jar
java -jar Client-0.1.0.jar

CORRER FRONTEND (ficheiro .jar compilado em frontend/build/libs)
java -jar frontend-0.1.0.jar

ACEDER AO WEBSITE
https://localhost:8443
http://localhost:8080