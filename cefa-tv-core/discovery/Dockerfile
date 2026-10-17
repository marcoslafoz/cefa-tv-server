FROM alpine:3.20

RUN apk add --no-cache socat iproute2

COPY offer.sh /offer.sh
RUN chmod +x /offer.sh

EXPOSE 47808/udp

CMD ["socat", "-T2", "UDP4-RECVFROM:47808,reuseaddr,fork", "EXEC:/offer.sh"]
