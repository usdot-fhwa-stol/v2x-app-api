################## Install curl to enable health checks ##################
FROM registry.access.redhat.com/ubi9:9.6 AS ubi-micro-build
RUN mkdir -p /mnt/rootfs
RUN dnf install --installroot /mnt/rootfs curl jq \
  --releasever 9 --setopt install_weak_deps=false --nodocs -y; \
  dnf --installroot /mnt/rootfs clean all

################## Build custom user provider into keycloak ##################
FROM quay.io/keycloak/keycloak:26.4 as builder

ENV KC_DB=postgres
ENV KC_HEALTH_ENABLED=true

RUN /opt/keycloak/bin/kc.sh build

################## Integrate realm and theme ##################
FROM quay.io/keycloak/keycloak:26.4
COPY --from=ubi-micro-build /mnt/rootfs /
COPY --from=builder /opt/keycloak/ /opt/keycloak/

COPY ./realm.json /opt/keycloak/data/import/realm.json

HEALTHCHECK --interval=5s --timeout=10s --start-period=30s --retries=30 \
  CMD curl --fail http://localhost:9000/health || exit 1
