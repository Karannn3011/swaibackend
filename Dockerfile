# --- Build Stage ---
# Use a Maven image that includes the JDK to build the application.
# This creates a temporary build environment.
FROM maven:3.9-eclipse-temurin-17 AS build

# Set the working directory inside the build container
WORKDIR /app

# Copy the Maven wrapper and pom.xml to leverage Docker cache
COPY .mvn/ .mvn
COPY mvnw pom.xml ./

# IMPORTANT: Grant execute permissions to the Maven wrapper
RUN chmod +x ./mvnw

# Download dependencies
RUN ./mvnw dependency:go-offline

# Copy the rest of your application's source code
COPY src ./src

# Build the application JAR file
RUN ./mvnw package -DskipTests


# --- Run Stage ---
# Use a lightweight JRE (Java Runtime Environment) image for the final container.
# This image is much smaller because it doesn't include the full JDK or Maven.
FROM eclipse-temurin:17-jre-jammy

# Set the working directory
WORKDIR /app

# Copy only the built JAR file from the 'build' stage into the final container
# and rename it to app.jar for simplicity.
COPY --from=build /app/target/*.jar app.jar

# Expose the port the application runs on (optional but good practice)
EXPOSE 8080

# The command to run the application using the copied JAR file
ENTRYPOINT ["java", "-jar", "app.jar"]