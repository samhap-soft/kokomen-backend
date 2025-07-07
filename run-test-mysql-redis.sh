#!/bin/bash

if ! docker ps --format '{{.Names}}' | grep -q '^test-mysql$'; then
  echo "test-mysql 컨테이너가 실행 중이 아닙니다. docker-compose -f test.yml up -d test-mysql로 시작합니다..."
  docker compose -f test.yml up -d test-mysql
else
  echo "test-mysql 컨테이너가 이미 실행 중입니다."
fi

if ! docker ps --format '{{.Names}}' | grep -q '^test-redis$'; then
  echo "test-redis 컨테이너가 실행 중이 아닙니다. docker-compose -f test.yml up -d test-redis로 시작합니다..."
  docker compose -f test.yml up -d test-redis
else
  echo "test-redis 컨테이너가 이미 실행 중입니다."
fi

# MySQL Health check 대기 (최대 30초, 1초 간격)
echo "test-mysql 컨테이너의 Health check 상태 확인 중..."
for i in {1..30}; do
  health_status=$(docker inspect --format='{{.State.Health.Status}}' test-mysql 2>/dev/null)

  if [ "$health_status" == "healthy" ]; then
    echo "test-mysql 컨테이너가 healthy 상태입니다."
    break
  elif [ "$health_status" == "unhealthy" ]; then
    echo "test-mysql 컨테이너가 unhealthy 상태입니다. 중단합니다."
    exit 1
  else
    echo "아직 healthy 아님 ($i초)..."
    sleep 1
  fi

  if [ $i -eq 30 ]; then
    echo "test-mysql 컨테이너가 30초 안에 healthy 상태가 되지 않았습니다. 중단합니다."
    exit 1
  fi
done

# Redis Health check 대기 (최대 30초, 1초 간격)
echo "test-redis 컨테이너의 Health check 상태 확인 중..."
for i in {1..30}; do
  health_status=$(docker inspect --format='{{.State.Health.Status}}' test-redis 2>/dev/null)

  if [ "$health_status" == "healthy" ]; then
    echo "test-redis 컨테이너가 healthy 상태입니다. 빌드를 시작합니다."
    break
  elif [ "$health_status" == "unhealthy" ]; then
    echo "test-redis 컨테이너가 unhealthy 상태입니다. 중단합니다."
    exit 1
  else
    echo "아직 healthy 아님 ($i초)..."
    sleep 1
  fi

  if [ $i -eq 30 ]; then
    echo "test-redis 컨테이너가 30초 안에 healthy 상태가 되지 않았습니다. 중단합니다."
    exit 1
  fi
done
