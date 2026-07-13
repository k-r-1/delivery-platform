# 🍗 배달 주문 관리 플랫폼, "*누가 내 음식을 훔쳤어?*"

**🗓️ 개발 기간: 2026.04.16 ~ 2026.04.30 (2주)**

> **🔗 원본 협업 저장소:** [WhoStoleMyfood/Backend](https://github.com/WhoStoleMyfood/Backend) — 팀 협업 당시의 [Pull Request](https://github.com/WhoStoleMyfood/Backend/pulls) · [Issue](https://github.com/WhoStoleMyfood/Backend/issues) 기록을 확인할 수 있습니다.

- 📌 배달의 민족, 쿠팡이츠와 유사한 배달 주문 관리 플랫폼으로, 음식점의 주문 관리, 결제, 리뷰, 주문 내역 관리 기능을 제공합니다. 또한 가게 운영자의 편의를 위해 선택적으로 AI 기반 메뉴 설명 생성 기능을 제공하여 메뉴 관리의 효율성을 높였습니다.

| **핵심 기능** | **설명** |
| --- | --- |
| 🏪 **가게 · 지역 · 카테고리 관리** | 지역 및 카테고리 기반 가게 등록과 운영 관리 |
| 🍽️ **메뉴 · AI 상품 설명 생성** | 가게별 메뉴 등록, Gemini AI를 활용한 메뉴 상품 설명 자동 생성 및 이력 관리 |
| ⭐ **리뷰 · 평점** | 완료된 주문에 한해 주문자만 리뷰 작성 가능, 리뷰 수정·삭제 및 삭제 후 재작성 가능, 자정마다 갱신되는 가게별 평균 평점 제공 |
| 🔐 **인증 · 권한 관리** | JWT 및 Redis 기반 회원가입, 로그인, 로그아웃 기능 구현, 권한별 API 접근 제어 |
| 👤 **사용자 관리** | 유저 정보 조회 및 수정, 관리자 전용 사용자 목록·상세 조회 기능 제공 |
| 🛒 **주문 · 배송지** | 5분 이내 주문 취소 제한 로직과 실시간 DB 권한 재검증을 적용한 안전한 주문 처리 |
| 💳 **결제** | Toss Payments 외부 API 기반 결제 승인 시스템 구축 |

<br>

## 👩‍💻 박주원(k-r-1) — 담당 도메인 & 리팩토링

주문·배송지 도메인과 팀 전체 통합 테스트를 담당했습니다. 프로젝트 종료 후에도 코드 품질을 높이기 위해 **권한 검증 중복 로직을 공통 컴포넌트로 추출하는 리팩토링**을 이어갔습니다.

📄 **[설계 · 트러블슈팅 · 리팩토링 상세 문서 →](./docs/contributions/박주원-order-address.md)**

<br>

## 👥 팀원 역할분담

| **성함** | **GitHub** | **역할 (Domain)** | **주요 업무 및 성과** |
| --- | --- | --- | --- |
| **유규리 (리더)** | <a href="https://github.com/yuguri76"><img src="https://img.shields.io/badge/GitHub-yuguri76-181717?style=flat-square&logo=github&logoColor=white"/></a> | Review / Review Rating / Area | 리뷰 및 평점 기능 구현, Area 도메인 구현, 통합 테스트 시나리오 작성 및 Postman 통합 테스트 진행, 아키텍처 설계, 협업구조 설계 |
| **박주원** | <a href="https://github.com/k-r-1"><img src="https://img.shields.io/badge/GitHub-k--r--1-181717?style=flat-square&logo=github&logoColor=white"/></a> | Order / Address | 주문 관리 기능 구현, Address 도메인 구현, 통합 테스트 코드 작성, 아키텍처 설계 |
| **박지은** | <a href="https://github.com/Jieunbakk"><img src="https://img.shields.io/badge/GitHub-Jieunbakk-181717?style=flat-square&logo=github&logoColor=white"/></a> | Search / Category | QueryDSL을 이용한 검색 기능 구현, Category 도메인 구현, API 문서 작성 |
| **이승민** | <a href="https://github.com/Cork-7"><img src="https://img.shields.io/badge/GitHub-Cork--7-181717?style=flat-square&logo=github&logoColor=white"/></a> | AI / Store / Menu | AI 연동 가게 및 메뉴 관리 기능 구현, Swagger 및 ERD 작성 |
| **김영욱** | <a href="https://github.com/kimyounguk1"><img src="https://img.shields.io/badge/GitHub-kimyounguk1-181717?style=flat-square&logo=github&logoColor=white"/></a> | Payment / Infra | 결제 기능 구현, docker-compose 기반 CI/CD 파이프라인 구축 |
| **박소윤** | <a href="https://github.com/musoyou12"><img src="https://img.shields.io/badge/GitHub-musoyou12-181717?style=flat-square&logo=github&logoColor=white"/></a> | Auth / User | Redis 기반 인증 인가 시스템 구축 및 조회 성능 최적화 |


<br>

## 🚀 서비스 구성 및 실행 방법

### Prerequisites

- **Java 17 (OpenJDK)**
- **Docker & Docker Compose** (PostgreSQL, Redis 실행용)
- **Google Gemini API Key**

### Installation (Getting Started)

#### 1. Setup Infrastructure
Launch PostgreSQL and Redis containers as defined in the project root:

```bash
# Start Docker containers (Detached mode)
docker-compose up -d
```

#### 2. Environment Configuration (.env)
Create a `.env` file in the root directory. Note: Database credentials must align with `docker-compose.yml` (Database: `mydb`).

```properties
# Database (Matched with docker-compose.yml)
DB_URL=jdbc:postgresql://localhost:5432/mydb
DB_USERNAME=myuser
DB_PASSWORD=mypassword

# Security & Authentication
JWT_SECRET=jwt_secret_key
JWT_ACCESS_MINUTE=100
JWT_REFRESH_MINUTE=100
ADMIN_SIGNUP_TOKEN=admin_signup_token

# External API Integration (Gemini AI)
AI_API_KEY=google_gemini_api_key
BASE_URL=https://generativelanguage.googleapis.com/v1
MODEL=gemini-2.5-flash
PAYMENT_KEY=test_gsk_docs_OaPz8L5KdmQXkzRz3y47BMw6
```

#### 3. Run Application

```bash
# Set execution permission and run
chmod +x gradlew
./gradlew bootRun
```

### 🌐 Deployment (Infrastructure)

- **Live Endpoint:** http://54.116.81.6:8080
- **Environment**: AWS EC2 t3.small (Ubuntu 24.04 LTS)
- **CI/CD**: GitHub Actions integrated with Docker Hub (duddnr08/sparta1)

<br>

## 🔍 프로젝트 상세 및 검증 성과

### 🛠 Tech Stack
> 
- **💻 Back-end & Data**
    - **Framework**: Spring Boot 3.5.13, Spring Security (JWT 0.12.7)
    - **Language**: Java 17
    - **Database**: PostgreSQL (Main), Redis (Cache & Session)
    - **ORM & Query**: Spring Data JPA, QueryDSL 7.0
- **🚀 Infra & External API**
    - **AI Integration**: Google Gemini API (Generative AI)
    - **Infrastructure**: AWS EC2, Docker, GitHub Actions (CI/CD)
    - **Documentation**: Swagger UI (OpenAPI 3.0)

<br>

### 📑 Technical Documentation

#### 💻 Architecture
<img width="2048" height="957" alt="image" src="https://github.com/user-attachments/assets/aa6fb291-e550-4090-9b89-82717aee1fea" />


#### 🧬 **ERD DIAGRAM**
<img width="956" height="807" alt="image" src="https://github.com/user-attachments/assets/530d5ba7-fcdf-4fb5-84ef-7f9a3ed4a510" />

### ⚠️ Troubleshooting

<details>
<summary>📈 리뷰 평점 집계 성능 최적화 (N+1 문제 해결)</summary>

- **문제(Problem)**: 가게 목록 조회 시 평균 평점을 실시간으로 집계하면서 리뷰 수만큼 추가 쿼리가 발생하는 **N+1 문제** 발생. 리뷰 데이터가 늘어날수록 DB 부하가 기하급수적으로 커지는 병목 현상 확인.
- **해결(Solution)**: 실시간 집계 방식 대신 **배치(Batch) 처리 전략** 도입. Spring Scheduler를 활용해 매일 자정 모든 가게의 평점을 미리 집계하여 전용 필드에 저장하도록 설계.
- **성과(Result)**: 리스트 조회 시 추가 연산 없이 데이터 서빙이 가능해졌으며, 조회 쿼리 수를 1개로 고정하여 DB 부하를 획기적으로 감소시킴.

</details>

<details>
<summary>🛡️ 리뷰 삭제 시 Soft Delete와 Unique 제약 조건 충돌 해결</summary>

- **문제(Problem)**: 리뷰 삭제 시 **Soft Delete**를 사용함에 따라 DB 로우는 남게 됨. 리뷰-주문의 1:1 관계(`order_id` Unique 제약) 때문에 사용자가 리뷰 삭제 후 재작성 시 DB 제약 조건 충돌로 인한 Insert 실패 발생.
- **해결(Solution)**: 신규 리뷰 생성 대신 **삭제된 로우 복구(Restore) 및 재사용** 로직 도입. `is_deleted=true`인 기존 데이터를 찾아 내용을 업데이트하고 상태를 복구하는 방식으로 전환.
- **성과(Result)**: DB의 물리적 제약 조건을 위반하지 않으면서도, 사용자에게는 리뷰 재작성 기능을 자연스럽게 제공하여 서비스 정합성과 UX를 동시에 확보.

</details>

<details>
<summary>⚡ GitHub Actions CI 빌드 속도 최적화 (Gradle Caching)</summary>

- **문제(Problem)**: CI/CD 파이프라인 가동 시 매 빌드마다 수백 개의 라이브러리를 새로 다운로드하면서 **약 1분 45초의 불필요한 대기 시간** 발생. 프로젝트 규모가 커질수록 빌드 병목 현상이 심화될 우려가 있음.
- **해결(Solution)**: `actions/cache` 플러그인을 도입하여 Gradle 의존성(Dependencies) 및 래퍼(Wrapper)를 캐싱 처리. 일부 라이브러리 변경 시에도 기존 캐시를 최대한 활용하는 **증분 빌드 전략** 적용.
- **성과(Result)**: CI 빌드 시간을 1분 45초에서 **57초로 약 45% 단축**. 코드 수정에 대한 검증 속도를 높여 전체적인 개발 및 배포 생산성을 극대화함.

</details>

<details>
<summary>🏛️ 통합 테스트 구조 개선</summary>

- **문제(Problem)**: Given 데이터를 모두 **MockMvc(API 호출)**로 생성하면서 검증하려는 기능과 직접적인 관련이 없는 API 호출까지 포함되어 테스트 코드가 길어지고 복잡해지는 문제가 발생
- **해결(Solution)**: **MockMvc 기반 E2E 테스트**와 **Repository Fixture**를 활용한 단일 시나리오 테스트로 분리
- **성과(Result)**: 테스트 목적이 명확해지고 가독성이 개선됨. 불필요한 API 호출과 토큰 발급을 줄여 테스트 구조를 단순화.

</details>

<details>
<summary>🔐 보안 강화를 위한 쿠키 기반 인증 구조 변경</summary>

- **문제 (Problem)**: Access/Refresh Token을 모두 HTTP 응답 바디(Body)로 전달하여, 자바스크립트가 토큰에 직접 접근할 수 있는 **XSS(Cross-Site Scripting) 공격** 시 탈취 위험이 큰 보안 취약점 존재.
- **해결 (Solution)**: RefreshToken을 브라우저 전용 보안 쿠키에 저장하도록 재설계. **`HttpOnly`**(JS 접근 차단), **`Secure`**(HTTPS 전용), **`SameSite=Strict`**(CSRF 방어) 옵션을 적용하고, `ResponseCookie` API를 통해 `Set-Cookie` 헤더를 정밀하게 제어.
- **성과 (Result)**: XSS 및 CSRF 등 외부 공격에 대한 방어력을 극대화하고, 인증 정보의 노출을 원천 차단하여 시스템 전반의 보안성과 무결성 확보.

</details>

<details>
<summary>🪪 실시간 권한 재검증 로직 도입으로 부정 주문 및 도용 방지</summary>

- **문제 (Problem)**: JWT의 무상태성(Stateless)으로 인해, 유저가 차단되거나 권한이 강등되어도 토큰이 만료되기 전까지는 시스템이 이를 실시간으로 인지하지 못함. 특히 결제와 직결된 '주문' 도메인에서 차단 유저가 여전히 주문을 넣을 수 있는 보안 취약점 발견.
- **해결 (Solution)**: 모든 API에 DB 조회를 넣는 대신, 결제 정합성이 중요한 Order, Address 서비스에 'DB 실시간 재검증(validateUserRoleFromDB)' 가드 로직을 추가함. 토큰의 유효성과 별개로 DB 상의 실제 유저 상태와 권한 일치 여부를 매 요청마다 교차 검증하는 하이브리드 인가(Defense in Depth) 구조 설계.
- **성과 (Result)**: 보안과 성능 사이의 트레이드오프를 최적화함. 실제 통합 테스트를 통해 유저 권한 변조 즉시, 유효한 토큰을 보유했더라도 주문 생성 및 배송지 조회가 실시간(403 Forbidden)으로 100% 차단됨을 검증하여 시스템 신뢰도 극대화.

> 실시간 권한 재검증 로직 패턴이 Review, Rating, Category 등 다른 도메인에도 적용되어 있음.

</details>

<details>
<summary>🔍 QueryDSL 동적 쿼리 최적화로 검색 성능 개선 및 중복 데이터 해결</summary>

#### 1. 불필요한 menu JOIN 제거로 검색 쿼리 성능 개선

- **문제 (Problem)**: 키워드 유무와 관계없이 항상 menu 테이블 LEFT JOIN이 실행되어 불필요한 성능 저하 발생.
- **원인 (Cause)**: 검색 조건(keyword)과 관계없이 menu JOIN이 고정으로 걸려 있어, 키워드 없는 일반 검색에서도 menu 테이블 전체를 조인함.
- **해결 (Solution)**: 키워드가 있을 때만 동적으로 menu JOIN이 적용되도록 쿼리 구조 개선.
- **성과 (Result)**: 가게 데이터 1만개, 메뉴 데이터 5만개 테스트 환경에서 119.8ms → 43.1ms 64%의 성능 개선

#### 2. `leftJoin(menu)` 중복 행 발생으로 인한 가게 검색 결과 중복 반환 해결

- **문제 (Problem)**: 가게 1개에 메뉴가 여러 개 있을 때 동일한 가게가 중복 반환됨.
- **원인 (Cause)**: `leftJoin(menu)`으로 인해 메뉴 수만큼 결과 행이 증가함.
- **해결 (Solution)**: `selectDistinct`를 적용하여 중복 가게 데이터 제거.

</details>

<br>

## API docs

- Swagger UI에서 API를 확인할 수 있습니다:
    - **Local**: [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)
    - **Live**: [http://54.116.81.6:8080/swagger-ui/index.html](http://54.116.81.6:8080/swagger-ui/index.html)
- **Notion (상세 API 명세 및 요청/응답 구조)**  
  - 👉 https://www.notion.so/API-docs-35297cddd34780f88833e3bbce298186?source=copy_link

