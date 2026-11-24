FROM openjdk:8u212-jre AS newjdk

FROM vulhub/solr:8.11.0
USER root
COPY --from=newjdk /usr/local/openjdk-8 /opt/java8new
ENV JAVA_HOME=/opt/java8new
ENV SOLR_JAVA_HOME=/opt/java8new
ENV PATH="/opt/java8new/bin:$PATH"
