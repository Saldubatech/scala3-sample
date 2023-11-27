#!/bin/bash

# 409f4ff80e54
docker run -d \
  -e "DB_HOST=409f4ff80e54" \
  -e "DB_PORT=5432" \
  -e "DB_USER=local_test" \
  -e "DB_PASSWORD=\$LocalTestSecreto$" \
  --network=test-network \
  --hostname app --name app-manual -p 8089:80 com.saldubatech/image:latest
