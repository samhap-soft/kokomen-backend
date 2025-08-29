# 꼬꼬면 (Kokomen) - AI 면접 준비 플랫폼

> **꼬리에 꼬리를 무는 면접 질문** - AI 기반 모의 면접 시스템

꼬꼬면은 개발자를 위한 AI 기반 모의 면접 플랫폼입니다. 실제 면접과 유사한 환경에서 꼬리질문을 통해 심화 학습할 수 있으며, 음성 및 텍스트 모드를 지원합니다.

## 🎯 핵심 기능

### 📝 AI 기반 면접 시스템
- **루트 질문**: 카테고리별 기본 면접 질문 (알고리즘, 데이터베이스, 자료구조, 네트워크, 운영체제)
- **꼬리 질문**: OpenAI GPT를 활용한 답변 기반 후속 질문 자동 생성
- **실시간 피드백**: 답변에 대한 즉시 평가 및 개선점 제시

### 🎙️ 다양한 면접 모드
- **텍스트 모드**: 키보드로 답변 입력
- **음성 모드**: Supertone TTS를 활용한 음성 질문 및 답변

### 📊 학습 분석 및 랭킹
- **답변 분석**: AI 기반 답변 품질 평가 및 점수화
- **사용자 랭킹**: 면접 성과 기반 순위 시스템
- **학습 기록**: 개인별 면접 히스토리 및 성장 추적

### 💬 소셜 기능
- **답변 공유**: 우수 답변 커뮤니티 공유
- **좋아요 시스템**: 답변에 대한 피어 평가
- **댓글 시스템**: 답변별 피드백 및 토론

## 🏗️ 시스템 아키텍처

### 마이크로서비스 구조
```
kokomen-backend/
├── api/           # 메인 REST API 서버
├── consumer/      # Kafka 이벤트 처리 서비스  
└── common/        # 공통 도메인 모델 및 유틸리티
```

### 핵심 기술 스택
- **Backend**: Spring Boot 3.x, Java 17
- **Database**: MySQL 8.0 (Primary), Redis (Session & Cache)
- **Message Queue**: Apache Kafka
- **AI Integration**: OpenAI GPT-4, AWS Bedrock
- **TTS**: Supertone Text-to-Speech
- **Authentication**: Kakao OAuth 2.0
- **File Storage**: AWS S3
- **Containerization**: Docker & Docker Compose

### 외부 서비스 연동
- **OpenAI GPT**: 질문 생성 및 답변 분석
- **AWS Bedrock**: 대안 LLM 서비스
- **Supertone**: 고품질 음성 합성
- **Kakao OAuth**: 소셜 로그인
- **AWS S3**: 음성 파일 저장

## 📋 주요 도메인 모델

### 면접 관리
- **Interview**: 면접 세션 정보
- **RootQuestion**: 카테고리별 기본 질문
- **Question**: AI 생성 후속 질문
- **Answer**: 사용자 답변 및 평가

### 사용자 관리  
- **Member**: 사용자 계정 및 프로필
- **MemberService**: 사용자 랭킹 및 토큰 관리

### 소셜 기능
- **AnswerLike**: 답변 좋아요 시스템
- **AnswerMemo**: 답변별 메모 및 댓글

## 🚀 빠른 시작

### 필수 요구사항
- Java 17+
- Docker & Docker Compose
- MySQL 8.0+
- Redis 6.0+

### 로컬 개발 환경 구성

1. **저장소 클론**
```bash
git clone https://github.com/your-org/kokomen-backend.git
cd kokomen-backend
```

2. **환경 변수 설정**
```bash
# 필수 환경변수
export OPEN_AI_API_KEY=your_openai_api_key
export KAKAO_CLIENT_ID=your_kakao_client_id
export KAKAO_CLIENT_SECRET=your_kakao_client_secret
export SUPERTONE_API_TOKEN=your_supertone_token
```

3. **의존성 서비스 실행**
```bash
# API 서버용 테스트 환경 
cd api
docker compose -f test.yml up -d
```

4. **애플리케이션 빌드 및 실행**
```bash
# 전체 빌드
./gradlew clean build

# API 서버 실행
./gradlew :api:bootRun

# Consumer 서비스 실행 (별도 터미널)
./gradlew :consumer:bootRun
```

5. **로컬 Docker 환경 실행 (권장)**
```bash
# API 서버 + 의존성 서비스
./local-run.sh

# Consumer 서비스 (별도 실행)
cd consumer
./local-run-consumer.sh
```

### API 접근
- API 서버: http://localhost:8080
- Health Check: http://localhost:8081/actuator/health
- API 문서: http://localhost:8080/docs/index.html

## 🔧 개발 가이드

### 테스트 실행
```bash
# 전체 테스트
./gradlew test

# 모듈별 테스트  
./gradlew :api:test
./gradlew :consumer:test
```

### 코드 품질 검사
```bash
# 정적 분석 (설정된 경우)
./gradlew check

# 테스트 커버리지 (설정된 경우)  
./gradlew jacocoTestReport
```

### 데이터베이스 마이그레이션
- Flyway를 통한 자동 마이그레이션
- 마이그레이션 파일: `api/src/main/resources/db/migration/`

## 🌐 배포 환경

### 프로필별 설정
- **local**: 로컬 개발 환경
- **dev**: 개발 서버 환경  
- **prod**: 운영 서버 환경
- **load-test**: 부하 테스트 환경

### Docker 배포
```bash
# API 서버 Docker 이미지 빌드
cd api && ./local-app-run.sh

# Consumer 서비스 Docker 이미지 빌드  
cd consumer && ./local-run-consumer.sh
```

## 📊 모니터링 및 운영

### 메트릭 수집
- **Spring Boot Actuator**: 애플리케이션 헬스체크
- **Prometheus**: 메트릭 수집 (설정 가능)
- **Custom Metrics**: 면접 진행률, API 응답시간 등

### 로깅
- **Structured Logging**: JSON 형태 로그 출력
- **MDC**: 요청별 추적 정보 
- **External Service Monitoring**: AI API 응답시간 추적

## 🔐 보안 고려사항

### 인증 및 인가
- Kakao OAuth 2.0 기반 소셜 로그인
- Session 기반 인증 (Redis 저장)
- CORS 정책 적용

### 데이터 보호
- 개인정보 최소 수집 원칙
- API 키 환경변수 관리
- 민감 정보 로그 제외

## 🤝 기여 가이드

### 브랜치 전략
- `main`: 운영 배포 브랜치
- `develop`: 개발 통합 브랜치  
- `feature/*`: 기능 개발 브랜치

### 커밋 컨벤션
```
feat: 새로운 기능 추가
fix: 버그 수정  
refactor: 코드 리팩토링
test: 테스트 추가/수정
docs: 문서 수정
```

## 📞 문의 및 지원

- **개발팀**: [팀 연락처]
- **이슈 리포트**: GitHub Issues
- **문서**: [위키 또는 문서 링크]

---

**꼬꼬면**으로 더 나은 면접 준비를 시작하세요! 🚀
