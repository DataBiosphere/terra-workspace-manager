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

RUN cd /jmeter/apache-jmeter-$JMETER_VERSION/ && wget -q -O /tmp/jpgc-functions-2.1.zip https://jmeter-plugins.org/files/packages/jpgc-functions-2.1.zip && unzip -n /tmp/jpgc-functions-2.1.zip && rm /tmp/jpgc-functions-2.1.zip
RUN cd /jmeter/apache-jmeter-$JMETER_VERSION/ && wget -q -O /tmp/bzm-parallel-0.9.zip https://jmeter-plugins.org/files/packages/bzm-parallel-0.9.zip && unzip -n /tmp/bzm-parallel-0.9.zip && rm /tmp/bzm-parallel-0.9.zip

ENV JMETER_HOME /jmeter/apache-jmeter-$JMETER_VERSION/
ENV PATH $JMETER_HOME/bin:$PATH

EXPOSE 60000