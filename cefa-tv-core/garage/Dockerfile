ARG GARAGE_IMAGE=dxflrs/garage:v2.1.0
FROM ${GARAGE_IMAGE} AS garage

FROM alpine:3.20
COPY --from=garage /garage /usr/local/bin/garage
COPY init.sh /usr/local/bin/garage-init.sh
RUN chmod +x /usr/local/bin/garage-init.sh
ENTRYPOINT ["/usr/local/bin/garage-init.sh"]
