FROM nginx:1.28.2-alpine
ARG PPE_GIT_COMMIT=uncommitted
LABEL org.opencontainers.image.revision=$PPE_GIT_COMMIT
COPY nginx.conf /etc/nginx/nginx.conf
USER 101:101
ENTRYPOINT ["nginx"]
CMD ["-g", "daemon off;"]
