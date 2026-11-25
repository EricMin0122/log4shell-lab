# syntax=docker/dockerfile:1
# victim-v1 | 조건 1: log4j 2.14.1 + 구 JDK(8u181, <8u191)
#   trustURLCodebase 기본 true => JNDI/LDAP 원격 클래스 로딩(RCE) 가능
#
# 설계: 베이스는 "고정 패치레벨 JRE" 이미지, Tomcat 배포본은 공식 tomcat 이미지의
#       안정 경로(/usr/local/tomcat)에서 통째로 복사한다(=JRE 내부 경로 추측 불필요).
#       Tomcat 이 catalina.jar / el-api.jar 를 제공하므로 BeanFactory+EL 가젯도 상주.

# ---- build: 취약 WAR (log4j 2.14.1) ----
FROM maven:3.9-eclipse-temurin-8 AS build
WORKDIR /src
COPY vuln-app/pom.xml .
COPY vuln-app/src ./src
ARG LOG4J_VERSION=2.14.1
RUN mvn -q -DskipTests -Dlog4j.version=${LOG4J_VERSION} package

# ---- tomcat 배포본(안정 경로 /usr/local/tomcat) ----
FROM tomcat:9.0-jre8-temurin AS tomcatdist

# ---- runtime: 고정 JRE 8u181 ----
FROM openjdk:8u181-jre
COPY --from=tomcatdist /usr/local/tomcat /usr/local/tomcat
ENV CATALINA_HOME=/usr/local/tomcat
ENV PATH="/usr/local/tomcat/bin:${PATH}"
RUN rm -rf /usr/local/tomcat/webapps/*
COPY --from=build /src/target/ROOT.war /usr/local/tomcat/webapps/ROOT.war
EXPOSE 8080
CMD ["catalina.sh", "run"]
