# Start with the latest cloud_sql_proxy image
FROM gcr.io/cloudsql-docker/gce-proxy:latest

RUN apk update && apk add bash git curl jq

# Copy in our script and set it to run
COPY entrypoint.sh /entrypoint.sh
ENTRYPOINT ["/entrypoint.sh"]
