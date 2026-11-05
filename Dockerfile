FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -B test package
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN apk add --no-cache chromium chromium-chromedriver ttf-liberation font-noto su-exec \
    && addgroup -S app && adduser -S -G app app && mkdir /data && chown app:app /data
ENV CHROME_BIN=/usr/bin/chromium-browser
ENV CHROMEDRIVER_PATH=/usr/bin/chromedriver
COPY --from=build /build/target/*.jar app.jar
COPY docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh
RUN chmod 755 /usr/local/bin/docker-entrypoint.sh
EXPOSE 8080
ENTRYPOINT ["/usr/local/bin/docker-entrypoint.sh"]
