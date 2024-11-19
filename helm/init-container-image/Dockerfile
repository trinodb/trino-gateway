FROM registry.access.redhat.com/ubi9/ubi-minimal:latest

RUN microdnf install -y python3

COPY ./bin/interpolate_env_vars.py /tmp/

ENTRYPOINT ["python3", "/tmp/interpolate_env_vars.py"]
