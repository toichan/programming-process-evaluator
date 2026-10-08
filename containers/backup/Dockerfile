FROM alpine:3.22.2
RUN apk add --no-cache age
ARG PPE_GIT_COMMIT=uncommitted
LABEL org.opencontainers.image.revision=$PPE_GIT_COMMIT
USER 65532:65532
ENTRYPOINT ["age"]
