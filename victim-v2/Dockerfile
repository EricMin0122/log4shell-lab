# syntax=docker/dockerfile:1
# victim-v2 | 조건 2: log4j 2.14.1 + 신 JDK(8u212, >=8u191)
#            조건 3: 동일 환경 + 로컬 가젯 체인(Tomcat BeanFactory+EL) 페이로드
#   trustURLCodebase 기본 false => 원격 로딩 차단(조건 2),
#   그러나 피해자 클래스패스의 org.apache.naming.factory.BeanFactory 로 로컬 우회 RCE(조건 3)
#
# 조건 2 와 3 은 "동일 victim-v2, 페이로드만 차이".

FROM maven:3.9-eclipse-temurin-8 AS build
WORKDIR /src
COPY vuln-app/pom.xml .
COPY vuln-app/src ./src
ARG LOG4J_VERSION=2.14.1
RUN mvn -q -DskipTests -Dlog4j.version=${LOG4J_VERSION} package

FROM tomcat:9.0-jre8-temurin AS tomcatdist

FROM openjdk:8u212-jre
COPY --from=tomcatdist /usr/local/tomcat /usr/local/tomcat
ENV CATALINA_HOME=/usr/local/tomcat
ENV PATH="/usr/local/tomcat/bin:${PATH}"
RUN rm -rf /usr/local/tomcat/webapps/*
COPY --from=build /src/target/ROOT.war /usr/local/tomcat/webapps/ROOT.war
EXPOSE 8080
CMD ["catalina.sh", "run"]
