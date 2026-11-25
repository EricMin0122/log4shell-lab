# syntax=docker/dockerfile:1
# victim-v3 | 조건 4(대조군): log4j 2.17.1 + JDK 11
#   JndiLookup 제거 => JNDI 질의 자체가 발생하지 않음.
# JDK 11 은 패치레벨 고정이 불필요하므로 공식 tomcat:9.0-jre11-temurin 베이스를 그대로 사용.

FROM maven:3.9-eclipse-temurin-8 AS build
WORKDIR /src
COPY vuln-app/pom.xml .
COPY vuln-app/src ./src
ARG LOG4J_VERSION=2.17.1
RUN mvn -q -DskipTests -Dlog4j.version=${LOG4J_VERSION} package

FROM tomcat:9.0-jre11-temurin
RUN rm -rf /usr/local/tomcat/webapps/*
COPY --from=build /src/target/ROOT.war /usr/local/tomcat/webapps/ROOT.war
EXPOSE 8080
