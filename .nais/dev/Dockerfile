FROM bellsoft/liberica-openjdk-alpine:21.0.7@sha256:143e4c24da2872fad6803fcbde5d335bbbe47dddb8ebf20fd916c02bdc7f463b

COPY build/libs/*.jar app.jar

ENV TZ="Europe/Oslo"
ENV JDK_JAVA_OPTIONS="-XX:MaxRAMPercentage=75"

ENTRYPOINT ["java", "-jar", "app.jar"]