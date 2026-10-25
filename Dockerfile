FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn clean package -DskipTests -q

FROM eclipse-temurin:21-jre-jammy

RUN apt-get update && apt-get install -y --no-install-recommends curl ca-certificates openssl \
    && curl -o /tmp/global-bundle.pem https://truststore.pki.rds.amazonaws.com/global/global-bundle.pem \
    && csplit -s -z -f /tmp/rds-ca- /tmp/global-bundle.pem '/-----BEGIN CERTIFICATE-----/' '{*}' \
    && for cert in /tmp/rds-ca-*; do \
         keytool -importcert -noprompt -alias "rds-ca-$(basename "$cert")" \
           -file "$cert" -cacerts -storepass changeit; \
       done \
    && rm -rf /tmp/rds-ca-* /tmp/global-bundle.pem /var/lib/apt/lists/*

RUN groupadd -r officyna && useradd -r -g officyna officyna
WORKDIR /app
COPY --from=builder /app/target/officyna-billing-service-*.jar app.jar
RUN chown -R officyna:officyna /app
USER officyna

EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts", \
    "-Djavax.net.ssl.trustStorePassword=changeit", \
    "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
