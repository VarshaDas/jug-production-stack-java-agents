# AgentCore Runtime requires a linux/arm64 container listening on port 8080
# exposing /invocations and /ping (provided by spring-ai-agentcore-runtime-starter).
FROM public.ecr.aws/docker/library/eclipse-temurin:25-jre

WORKDIR /app
COPY build/libs/hello-spring-ai-bedrock.jar /app/app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
