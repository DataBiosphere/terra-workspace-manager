### This Dockerfile is used to extend the app-sec blessed Docker image for JRE-17
### We extend the base image to include a PostgreSQL client -- the client is primarily used
### for pg_dump commands to backup/restore the database.

### Sourced from https://github.com/broadinstitute/dsp-appsec-blessed-images/blob/main/jre/Dockerfile.17-debian
FROM us.gcr.io/broad-dsp-gcr-public/base/jre:17-debian

# Add postgres client for pg_dump command and clean up
RUN apt-get update  \
    && apt-get install postgresql-client-14 -y --no-install-recommends \
    && apt-get clean  \
    && rm -rf /var/lib/apt/lists/*
