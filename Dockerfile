# Use an official Maven image with Java 17
FROM maven:3.9-eclipse-temurin-17

# Install Chromium (Linux open-source Chrome) and ChromeDriver
RUN apt-get update && apt-get install -y \
    chromium \
    chromium-driver \
    && rm -rf /var/lib/apt/lists/*

# Set up the working directory inside the server
WORKDIR /app

# Copy your code into the cloud server
COPY . .

# Compile the Java application
RUN mvn clean package

# Command to run your background scheduler
CMD ["mvn", "exec:java"]