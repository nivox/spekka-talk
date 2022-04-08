#!/bin/sh

ROOT_DIR=$(cd $(dirname $0)/../; pwd)

KAFKA_SERVER=spekka_kafka.spekkatalk:9092
KAFKA_OUTPUT_TOPIC=readings

case $MODE in
  "single")
    DEPLOYMENTS=d1:1@1
    ;;
  "multi")
    DEPLOYMENTS=d1:1@1,d2:2@2,d3:3@3
    ;;
  *)
    echo "Invalid MODE: specify either single or multi"
    exit 1
    ;;
esac

docker run --rm --net spekkatalk -v $ROOT_DIR:/workspace \
  --name spekka_pusher \
  -e JAVA_opts="-Xmx256M" \
  -e KAFKA_SERVER="$KAFKA_SERVER" \
  -e KAFKA_OUTPUT_TOPIC="$KAFKA_OUTPUT_TOPIC" \
  -e DEPLOYMENTS="$DEPLOYMENTS" \
  eclipse-temurin:11 /workspace/target/pack/bin/pusher

