FROM gradle:8.10.2-jdk21 AS build
WORKDIR /workspace
COPY build.gradle settings.gradle ./
COPY src ./src
COPY docs/rubric ./docs/rubric
RUN --mount=type=cache,target=/home/gradle/.gradle gradle --no-daemon clean build --warning-mode all \
    && mkdir -p /opt/gradle-cache && cp -a /home/gradle/.gradle/caches /opt/gradle-cache/
RUN mkdir -p runtime && cd runtime && jar xf ../build/libs/ROOT.war

FROM build AS tools
ARG PPE_GIT_COMMIT=uncommitted
LABEL org.opencontainers.image.revision=$PPE_GIT_COMMIT
ENV GRADLE_USER_HOME=/opt/gradle-cache
COPY --chmod=0644 containers/production/secrets-entrypoint.sh /usr/local/bin/ppe-tools
ENTRYPOINT ["sh", "/usr/local/bin/ppe-tools"]
CMD ["gradle", "--offline", "--no-daemon", "flywayMigrate", "flywayValidate", "flywayInfo"]

FROM tomcat:9.0.118-jdk21-temurin AS app
ARG PPE_GIT_COMMIT=uncommitted
LABEL org.opencontainers.image.revision=$PPE_GIT_COMMIT
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* /usr/local/tomcat/webapps/* \
    && groupadd -g 10001 ppe && useradd -u 10001 -g ppe -d /usr/local/tomcat ppe \
    && chown -R ppe:ppe /usr/local/tomcat
COPY --from=build --chown=ppe:ppe /workspace/build/libs/ROOT.war /usr/local/tomcat/webapps/ROOT.war
COPY --chmod=0644 containers/production/context.xml /usr/local/tomcat/conf/context.xml
COPY --chmod=0644 containers/production/server.xml /usr/local/tomcat/conf/server.xml
COPY --chmod=0644 containers/production/secrets-entrypoint.sh /usr/local/bin/ppe-app
RUN mkdir /tmp/war-check && cd /tmp/war-check && jar xf /usr/local/tomcat/webapps/ROOT.war \
    && sed -i '/<http-only>true<\/http-only>/a\            <secure>true</secure>' WEB-INF/web.xml \
    && jar uf /usr/local/tomcat/webapps/ROOT.war WEB-INF/web.xml \
    && rm -rf /tmp/war-check
USER 10001:10001
ENV PPE_ENV=production
EXPOSE 8080
ENTRYPOINT ["sh", "/usr/local/bin/ppe-app"]
CMD ["catalina.sh", "run"]
