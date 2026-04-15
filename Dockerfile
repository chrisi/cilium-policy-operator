FROM apwessnacr001.azurecr.io/copk8s/alpine/jre25-hotspot:3.4.16
ARG JAR_FILE
ADD target/${JAR_FILE} /app/app.jar

# revelant only locally, chart sets its own command
ENTRYPOINT ["java","-jar","/app/app.jar"]
