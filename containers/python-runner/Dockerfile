FROM python:3.12-alpine

RUN apk add --no-cache docker-cli

WORKDIR /runner
COPY runner.py /runner/runner.py

ENV PYTHONDONTWRITEBYTECODE=1
USER 0:0
EXPOSE 8090
CMD ["python3", "-I", "-S", "-B", "/runner/runner.py"]
