#!/bin/bash
set -e

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_FILE="$SCRIPT_DIR/docker-compose-prod.yml"
HEALTH_TIMEOUT=120
HEALTH_INTERVAL=5
GRACEFUL_SHUTDOWN_WAIT=65

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

wait_healthy() {
    local container=$1
    local elapsed=0

    log_info "헬스체크 대기: $container (타임아웃: ${HEALTH_TIMEOUT}초)"

    while [ $elapsed -lt $HEALTH_TIMEOUT ]; do
        status=$(docker inspect --format='{{.State.Health.Status}}' "$container" 2>/dev/null || echo "starting")
        if [ "$status" = "healthy" ]; then
            echo ""
            log_info "헬스체크 통과! (${elapsed}초 소요)"
            return 0
        fi
        sleep $HEALTH_INTERVAL
        elapsed=$((elapsed + HEALTH_INTERVAL))
        echo -n "."
    done

    echo ""
    log_error "헬스체크 실패: 타임아웃 (${HEALTH_TIMEOUT}초)"
    return 1
}

main() {
    log_info "========== Blue-Green 배포 시작 =========="

    CURRENT=$(get_active)
    log_info "현재 활성 환경: $CURRENT"

    if [ "$CURRENT" = "blue" ]; then
        TARGET="green"
        OLD="kokomen-api-blue"
    elif [ "$CURRENT" = "green" ]; then
        TARGET="blue"
        OLD="kokomen-api-green"
    else
        TARGET="blue"
        OLD=""
        log_info "최초 배포: blue 환경으로 시작"
    fi

    log_info "타겟 환경: $TARGET"

    # Step 1: Traefik이 실행 중인지 확인
    if ! docker ps -q -f name=traefik | grep -q .; then
        log_info "Step 0: Traefik 시작"
        sudo -E docker compose -f $COMPOSE_FILE up -d traefik
        sleep 3
    fi

    # Step 2: 새 컨테이너 시작
    log_info "Step 1: $TARGET 컨테이너 시작"
    sudo -E docker compose -f $COMPOSE_FILE --profile $TARGET up -d "kokomen-api-$TARGET"

    # Step 3: 헬스체크 대기
    log_info "Step 2: 헬스체크 수행"
    if ! wait_healthy "kokomen-api-$TARGET"; then
        log_error "배포 실패: 새 컨테이너 헬스체크 실패"
        log_warn "롤백: 새 컨테이너 제거"
        docker rm -f "kokomen-api-$TARGET" 2>/dev/null || true
        exit 1
    fi

    # Step 4: Traefik 라우팅 안정화 대기
    log_info "Step 3: Traefik 라우팅 안정화 대기"
    sleep 5

    # Step 5: 기존 컨테이너 graceful 종료
    if [ -n "$OLD" ]; then
        log_info "Step 4: 기존 컨테이너 종료 ($OLD, ${GRACEFUL_SHUTDOWN_WAIT}초 대기)"
        docker stop -t $GRACEFUL_SHUTDOWN_WAIT "$OLD" || true
        docker rm -f "$OLD" 2>/dev/null || true
        log_info "기존 컨테이너 종료 완료"
    fi

    # Step 6: 완료 확인
    log_info "Step 5: 배포 완료 확인"
    if curl -sf "http://localhost:80/actuator/health" > /dev/null 2>&1; then
        log_info "========== 배포 성공! =========="
        log_info "활성 환경: $TARGET"
    else
        log_warn "경고: 외부 헬스체크 실패 (Traefik 라우팅 확인 필요)"
    fi
}

main "$@"
