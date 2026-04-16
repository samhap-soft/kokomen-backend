#!/bin/bash
set -e

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_FILE="$SCRIPT_DIR/docker-compose-prod.yml"
HEALTH_TIMEOUT=120
HEALTH_INTERVAL=5

log_info() { echo "[INFO] $1"; }
log_warn() { echo "[WARN] $1"; }
log_error() { echo "[ERROR] $1"; }

get_active() {
    if docker ps -q -f name=kokomen-api-blue | grep -q .; then
        echo "blue"
    elif docker ps -q -f name=kokomen-api-green | grep -q .; then
        echo "green"
    else
        echo "none"
    fi
}

check_container_exists() {
    local container=$1
    if docker ps -a -q -f name="$container" | grep -q .; then
        return 0
    else
        return 1
    fi
}

wait_healthy() {
    local container=$1
    local elapsed=0

    log_info "헬스체크 대기: $container"

    while [ $elapsed -lt $HEALTH_TIMEOUT ]; do
        status=$(docker inspect --format='{{.State.Health.Status}}' "$container" 2>/dev/null || echo "starting")
        if [ "$status" = "healthy" ]; then
            echo ""
            log_info "헬스체크 통과!"
            return 0
        fi
        sleep $HEALTH_INTERVAL
        elapsed=$((elapsed + HEALTH_INTERVAL))
        echo -n "."
    done

    echo ""
    log_error "헬스체크 실패"
    return 1
}

main() {
    log_info "========== 롤백 시작 =========="

    CURRENT=$(get_active)
    log_info "현재 활성 환경: $CURRENT"

    if [ "$CURRENT" = "blue" ]; then
        ROLLBACK_TARGET="green"
    elif [ "$CURRENT" = "green" ]; then
        ROLLBACK_TARGET="blue"
    else
        log_error "현재 활성 환경을 확인할 수 없습니다"
        exit 1
    fi

    log_info "롤백 대상: $ROLLBACK_TARGET"

    # 롤백 대상 컨테이너 확인
    if check_container_exists "kokomen-api-$ROLLBACK_TARGET"; then
        # 컨테이너가 존재하면 시작
        log_info "기존 컨테이너 시작"
        docker start "kokomen-api-$ROLLBACK_TARGET" || true
    else
        # 컨테이너가 없으면 새로 생성
        log_info "롤백 대상 컨테이너 생성"
        sudo -E docker compose -f $COMPOSE_FILE --profile $ROLLBACK_TARGET up -d "kokomen-api-$ROLLBACK_TARGET"
    fi

    # 헬스체크
    if ! wait_healthy "kokomen-api-$ROLLBACK_TARGET"; then
        log_error "롤백 실패: 헬스체크 실패"
        exit 1
    fi

    # Traefik 라우팅 안정화
    sleep 5

    # 현재 활성 컨테이너 종료
    log_info "현재 컨테이너 종료: kokomen-api-$CURRENT"
    docker stop -t 65 "kokomen-api-$CURRENT" || true
    docker rm -f "kokomen-api-$CURRENT" 2>/dev/null || true

    log_info "========== 롤백 완료 =========="
    log_info "활성 환경: $ROLLBACK_TARGET"
}

main "$@"
