#!/bin/sh

ROOT_DIR=$(cd $(dirname $0)/../; pwd)

APP_NAME=app1-plain-flow
KAFKA_SERVER=kafka.spekkatalk:9092
KAFKA_INPUT_TOPIC=readings

docker run --rm --net spekkatalk -v $ROOT_DIR:/workspace \
  -p 8123:8080 \
  --name spekka_app \
  -e KAFKA_SERVER="$KAFKA_SERVER" \
  -e KAFKA_INPUT_TOPIC="$KAFKA_INPUT_TOPIC" \
  eclipse-temurin:11 /workspace/target/pack/bin/$APP_NAME

