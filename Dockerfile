# Build stage: Red Hat UBI OpenJDK 17 image ships with Maven
FROM registry.access.redhat.com/ubi9/openjdk-17:latest AS build
USER root
WORKDIR /build
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests

# Runtime stage: runs any jar in /deployments with container-aware JVM settings.
# Works with OpenShift's arbitrary non-root UID (restricted-v2 SCC).
FROM registry.access.redhat.com/ubi9/openjdk-17-runtime:latest
COPY --from=build /build/target/neet-question-chat-*.jar /deployments/app.jar
# The question papers are indexed from disk at startup.
COPY Questions /deployments/Questions
ENV NEET_DOCUMENTS_DIRECTORY=/deployments/Questions
EXPOSE 8080
USER 185
