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

    # Step 1: 라우팅 전제 확인
    #
    # traefik은 이 compose에 없다. client 레포의 kokomen-traefik-prod가 한 호스트의
    # 유일한 내부 라우터이고, docker provider로 아래 컨테이너 라벨을 읽어 라우팅한다.
    # 여기서는 그것이 떠 있는지만 확인한다. 없으면 API를 올려도 외부에서 도달하지 못한다.
    #
    # -f name= 은 부분 일치이므로 정확히 비교한다. 예전 코드는 name=traefik 으로
    # 검사해 dev의 kokomen-traefik 에도 매칭됐다.
    if ! docker ps --format '{{.Names}}' | grep -qx 'kokomen-traefik-prod'; then
        log_error "kokomen-traefik-prod 가 실행 중이 아닙니다."
        log_error "  이 컨테이너는 client 레포(docker/client/compose.yaml)가 소유합니다."
        log_error "  client 배포를 먼저 실행하거나 아래로 직접 기동하세요:"
        log_error "    cd <client 워크스페이스> && docker compose -f docker/client/compose.yaml up -d traefik"
        exit 1
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
    log_info "========== 배포 성공! =========="
    log_info "활성 환경: $TARGET"
}

main "$@"
