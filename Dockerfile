
# Stage 1: Build the application
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

# Load Google's Maven mirror configuration
COPY settings.xml /root/.m2/settings.xml

# Copy project files
COPY pom.xml .
COPY src ./src

# Build Spring Boot application
RUN mvn -B clean package -DskipTests

# Stage 2: Run the application
FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
