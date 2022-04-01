#!/bin/sh

ROOT_DIR=$(cd $(dirname $0)/../; pwd)

KAFKA_SERVER=kafka.spekkatalk:9092
KAFKA_OUTPUT_TOPIC=readings
DEPLOYMENTS=d1:1@0.5

docker run --rm --net spekkatalk -v $ROOT_DIR:/workspace \
  --name spekka_pusher \
  -e KAFKA_SERVER="$KAFKA_SERVER" \
  -e KAFKA_OUTPUT_TOPIC="$KAFKA_OUTPUT_TOPIC" \
  -e DEPLOYMENTS="$DEPLOYMENTS" \
  eclipse-temurin:11 /workspace/target/pack/bin/pusher

