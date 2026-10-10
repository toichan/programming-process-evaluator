FROM python:3.12-alpine
ARG PPE_GIT_COMMIT=uncommitted
LABEL org.opencontainers.image.revision=$PPE_GIT_COMMIT

RUN apk add --no-cache docker-cli

WORKDIR /runner
COPY runner.py /runner/runner.py
RUN chmod 0755 /runner && chmod 0644 /runner/runner.py

ENV PYTHONDONTWRITEBYTECODE=1
USER 0:0
EXPOSE 8090
CMD ["python3", "-I", "-S", "-B", "/runner/runner.py"]
