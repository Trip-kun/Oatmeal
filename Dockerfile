FROM gradle:jdk21 as BUILD
WORKDIR /build
COPY --chown=gradle:gradle src /build/src
COPY --chown=gradle:gradle build.gradle settings.gradle /build/
RUN gradle --no-daemon shadowJar

FROM openjdk:21-slim
RUN addgroup --system --gid 1001 app && \
    adduser --system --uid 1001 --gid 1001 app
WORKDIR /app
COPY --from=BUILD /build/build/libs/oatmeal.jar /app/oatmeal.jar
RUN chown -R app:app /app
WORKDIR /app/work
USER app
VOLUME /app/work
# As this is not a webapp, there's not a good way to implement a healthcheck at the moment, but we'll keep this here for future reference
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC"
ENTRYPOINT java $JAVA_OPTS -jar /app/oatmeal.jar