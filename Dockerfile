# Build context is the ApexWallet directory, after `mvn -DskipTests package`.
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY apexwallet-app/target/apexwallet-app-*.jar /app/app.jar
EXPOSE 8080
USER 1000
HEALTHCHECK --interval=15s --timeout=5s --retries=8 CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/8080'
ENV SPRING_PROFILES_ACTIVE=local
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
