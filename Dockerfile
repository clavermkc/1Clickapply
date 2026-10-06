# Image d'exécution uniquement : le jar est compilé AVANT sur ta machine
#   ./gradlew bootJar -x test   ->  build/libs/1clickapply-0.0.1-SNAPSHOT.jar
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY build/libs/1clickapply-0.0.1-SNAPSHOT.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
CMD ["--mode=help"]
