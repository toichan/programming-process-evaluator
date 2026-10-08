FROM python:3.12-alpine
ARG PPE_GIT_COMMIT=uncommitted
LABEL org.opencontainers.image.revision=$PPE_GIT_COMMIT

WORKDIR /broker
COPY broker.py /broker/broker.py
ENV PYTHONDONTWRITEBYTECODE=1
EXPOSE 2375
CMD ["python3", "-I", "-S", "-B", "/broker/broker.py"]
