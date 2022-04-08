#!/bin/sh

ROOT_DIR=$(cd $(dirname $0)/../; pwd)

APP_NAME=$1
: ${APP_NAME:=app}

: ${APP_ID:=1}

KAFKA_SERVER=spekka_kafka.spekkatalk:9092
KAFKA_INPUT_TOPIC=readings

docker run --rm --net spekkatalk -v $ROOT_DIR:/workspace \
  -p $((8122 + APP_ID)):8080 \
  --name spekka-app-${APP_ID} \
  --hostname spekka-app-${APP_ID} \
  -e JAVA_OPTS="-Xmx512M" \
  -e KAFKA_SERVER="$KAFKA_SERVER" \
  -e KAFKA_INPUT_TOPIC="$KAFKA_INPUT_TOPIC" \
  eclipse-temurin:11 /workspace/target/pack/bin/$APP_NAME

