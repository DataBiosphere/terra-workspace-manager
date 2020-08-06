# This is the Dockerfile for the JMeter Worker
# TODO: Refactor common code from JMeter Director / Worker into a Base file

FROM openjdk:slim
MAINTAINER BroadInstitute

ARG JMETER_VERSION=5.3

RUN apt-get clean \
&& apt-get update \
&& apt-get -qy install \
wget \
telnet \
iputils-ping \
unzip

RUN mkdir /jmeter \
&& cd /jmeter/ \
&& wget https://archive.apache.org/dist/jmeter/binaries/apache-jmeter-$JMETER_VERSION.tgz \
&& tar -xzf apache-jmeter-$JMETER_VERSION.tgz \
&& rm apache-jmeter-$JMETER_VERSION.tgz

RUN cd /jmeter/apache-jmeter-$JMETER_VERSION/ \
&& wget -q -O /tmp/jpgc-functions-2.1.zip https://jmeter-plugins.org/files/packages/jpgc-functions-2.1.zip && unzip -n /tmp/jpgc-functions-2.1.zip && rm /tmp/jpgc-functions-2.1.zip \
&& wget -q -O /tmp/bzm-parallel-0.9.zip https://jmeter-plugins.org/files/packages/bzm-parallel-0.9.zip && unzip -n /tmp/bzm-parallel-0.9.zip && rm /tmp/bzm-parallel-0.9.zip \
&& wget -q -O lib/google-auth-library-oauth2-http-0.20.0.jar https://repo1.maven.org/maven2/com/google/auth/google-auth-library-oauth2-http/0.20.0/google-auth-library-oauth2-http-0.20.0.jar \
&& wget -q -O lib/google-auth-library-credentials-0.20.0.jar https://repo1.maven.org/maven2/com/google/auth/google-auth-library-credentials/0.20.0/google-auth-library-credentials-0.20.0.jar \
&& wget -q -O lib/google-http-client-1.34.0.jar https://repo1.maven.org/maven2/com/google/http-client/google-http-client/1.34.0/google-http-client-1.34.0.jar \
&& wget -q -O lib/google-http-client-jackson2-1.34.0.jar https://repo1.maven.org/maven2/com/google/http-client/google-http-client-jackson2/1.34.0/google-http-client-jackson2-1.34.0.jar \
&& wget -q -O lib/guava-28.2-jre.jar https://repo1.maven.org/maven2/com/google/guava/guava/28.2-jre/guava-28.2-jre.jar

# TODO: Pull custom libraries from cloud storage
# Currently, these libraries are simply copied from local directories relative to the working directory of Dockerfile
COPY automation-jmeter-plugins-0.1.0.jar /jmeter/apache-jmeter-$JMETER_VERSION/lib/ext/

ENV JMETER_HOME /jmeter/apache-jmeter-$JMETER_VERSION
ENV PATH $JMETER_HOME/bin:$PATH

EXPOSE 1099 50000

ENTRYPOINT $JMETER_HOME/bin/jmeter-server \
-Dserver.rmi.localport=50000 \
-Dserver_port=1099 \
-Jserver.rmi.ssl.disable=true