#!/bin/bash

dockerize -template /opt/gateway/gateway-ha-config.yml:/opt/gateway/gateway-config.yml

java -XX:MinRAMPercentage=50 -XX:MaxRAMPercentage=80 \
     --add-opens=java.base/java.lang=ALL-UNNAMED \
     --add-opens=java.base/java.net=ALL-UNNAMED \
     -jar gateway-ha.jar server gateway-config.yml
