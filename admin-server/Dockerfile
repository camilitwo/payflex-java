FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/*.jar app.jar
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseContainerSupport"
EXPOSE 9090
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar app.jar"]
