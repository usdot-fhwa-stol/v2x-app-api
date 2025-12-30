################## Install curl to enable health checks ##################
FROM alpine:latest AS tools
RUN apk add --no-cache curl
RUN curl -L https://github.com/jqlang/jq/releases/download/jq-1.7.1/jq-linux-amd64 -o /usr/local/bin/jq && \
  chmod +x /usr/local/bin/jq

################## Build custom user provider into keycloak ##################
FROM quay.io/keycloak/keycloak:26.4 as builder

ENV KC_DB=postgres
ENV KC_HEALTH_ENABLED=true

RUN /opt/keycloak/bin/kc.sh build

################## Integrate realm and theme ##################
FROM quay.io/keycloak/keycloak:26.4
USER root
COPY --from=tools /usr/local/bin/jq /usr/bin/jq
COPY --from=tools /usr/bin/curl /usr/bin/curl
USER 1000
COPY --from=builder /opt/keycloak/ /opt/keycloak/

COPY ./realm.json /opt/keycloak/data/import/realm.json

HEALTHCHECK --interval=5s --timeout=10s --start-period=30s --retries=30 \
  CMD curl --fail http://localhost:9000/health || exit 1
