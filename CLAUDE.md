# 악보 정리 앱 (sheet-music)

## 프로젝트 개요
예배/공연용 악보를 곡 단위로 관리하고, 셋리스트(콘티)를 구성하는 앱.

## 작업 원칙 (직렬 워크플로우, 한 번에 한 이슈)
- **이슈 = 세션 = 브랜치 1:1.** 이슈마다 별도 세션으로 분리하고, git도 별도 브랜치로 분리한다.
- ⚠️ 세션을 새로 열어도 워킹 트리(파일·diff)는 디스크에 그대로 남는다. **세션 분리 ≠ 작업 격리.** 격리는 브랜치로 보장한다.
- **한 번에 한 이슈만** 작업한다. 다음 이슈로 넘어가기 전에 현재 이슈를 커밋/PR로 마무리하거나 stash한다 (diff가 섞이지 않게).
- **main 직접 커밋 금지.** `feat/{이슈번호}-{설명}` / `fix/{이슈번호}-{설명}` 브랜치만 사용.
- main push = 자동배포이므로 **PR 머지로만 main에 반영**한다. 커밋 메시지에 `#이슈번호`를 넣어 이슈와 연결.
- 이슈 시작 → `git switch -c feat/#N-...` → 작업·커밋 → push → PR → merge → `git switch main && git pull` → 다음 이슈.

## 공통 작업 지침
- **구현 전 파악·설명.** 작업 전 파일 구조·의존성을 먼저 파악하고, 변경 범위·영향도를 설명한 뒤 구현한다.
- **큰 작업은 단계별 분할.** 스키마 → API → 화면 → 검증 순으로 나누고, 각 단계에서 중간 확인을 거친다.
- **레거시는 기존 패턴 유지.** 임의 리팩토링을 하지 않는다.
- **불확실하면 추측하지 말고 질문한다.**
- **임시 산출물 정리.** 작업 중 생긴 임시 파일·스크린샷·백업은 알아서 정리한다.
- **Obsidian vault 작업 전** `00_Rules/` 폴더를 먼저 읽는다.
- **에이전트 지시문(`IDENTITY.md` 등) 작성 시**: 금지 목록보다 좋은/나쁜 실제 예문을 병기하는 편이 효과적이다. 톤 지시는 단일 파일로 관리한다.

## 기술 스택
- **백엔드**: Java, Spring Boot, Gradle
- **프론트엔드**: Vue 3, TypeScript
- **DB**: PostgreSQL
- **인프라**: Docker Compose / OCI VM (백엔드) + Vercel (프론트엔드)
- **스토리지**: Cloudflare R2 (파일 업로드)

## 도메인 구조
```
songs (곡)
  └── song_sheets (악보 버전, 키별)
        └── song_files (악보 파일, PDF 등)

setlists (셋리스트/콘티)
  └── setlist_items (곡 순서, 어떤 악보 버전 쓸지 포함)
```

## 주요 설계 결정
- 삭제는 모두 **soft delete** (`deleted_at` 기록 방식)
- 파일 저장 경로: `./uploads/sheets/{songSheetId}/`
- 파일명은 UUID로 저장 (원본명 별도 보관)
- `sheet_key`, `version_name`은 선택사항, 같은 곡 안에서 동일 키 중복 허용

## API 구조 (README 기준)
| 도메인 | 경로 |
|--------|------|
| Songs | `/api/songs` |
| Song Sheets | `/api/songs/{songId}/sheets`, `/api/song-sheets/{id}` |
| Song Files | `/api/song-sheets/{id}/files`, `/api/song-files/{id}/view`, `/api/song-files/{id}/download` |
| Setlists | `/api/setlists` (구현 예정) |

## 구현된 주요 기능
- **곡/악보/파일**: songs·song_sheets·song_files CRUD, 곡 검색, soft delete
- **셋리스트**: setlists·setlist_items CRUD, 콘티 화면, 드래그앤드롭 순서 변경, 셋리스트 PDF 다운로드(jsPDF)
- **공유 링크**: UUID 토큰 기반 콘티 공유 (`/share/:token`), 공개 뷰
- **통합 등록/업로드**: 곡+악보 1단계 통합 등록, 일괄 업로드 화면(`/songs/bulk`, 드래그앤드롭 + 카드별 편집)
- **가사 관리**: songs.lyrics 컬럼, `PATCH /lyrics`, 곡 상세 가사 섹션(직접 편집)
- **태그**: song_tags 테이블(`@ElementCollection EAGER+SUBSELECT`), 태그 칩 입력/필터
- **링크**: song_links 테이블, 멀티 플랫폼(YouTube/Spotify/Melon 등) 자동 감지, YouTube 임베드 토글
- **통합 검색**: 제목·아티스트·가사 LIKE + 키 대소문자 무관 LIKE 필터 + 태그 필터
- **로딩 UX**: axios 인터셉터 전역 로딩 바 + 콘텐츠 딤 처리 (`useHttpLoading`)
- **사용자 인증**: Spring Security + JWT 자체 구현(`/api/auth/register`, `/api/auth/login`), 30일 만료 토큰. `/api/auth/**`, `GET /api/setlists/share/**`, `GET /api/song-files/*/view`·`/download`만 공개, 나머지 API는 인증 필요. 프론트엔드는 `authStore`(Pinia) + axios 인터셉터로 토큰 첨부, 라우터 가드로 미인증 시 `/login` 리다이렉트, 401 응답 시 자동 로그아웃
- **관리자 모드**(#156): `users.role`(USER/ADMIN) 역할 기반 권한. JWT 필터가 매 요청 시 DB에서 role 조회해 `ROLE_ADMIN`/`ROLE_USER` authority 주입(지정/해제 즉시 반영, 게스트는 관리자 불가). `/api/admin/**` → `hasRole('ADMIN')`. 관리 기능: 사용자 관리(목록/권한 지정·해제/삭제, 본인 대상 작업 차단), 콘텐츠 휴지통(삭제된 곡·콘티 조회·복구), 기능요청 상태변경·삭제. 프론트: `authStore.isAdmin`, `/admin` 라우트 가드 + 관리자에게만 보이는 진입 메뉴. 초기 관리자 시드: `jeybell`
- **UI**: shadcn-vue 스타일 + Tailwind v4 + 다크/라이트 테마 + 반응형, 악보 이미지 슬라이드 뷰어
- **인프라**: CI/CD 자동배포(GitHub Actions → OCI SSH 배포, Vercel Git 연동), Cloudflare R2 연동(STORAGE_TYPE 전환, 현재 local 모드)

## DB 마이그레이션 현황
| 버전 | 내용 |
|------|------|
| V1 | 초기 스키마 (songs, song_sheets, song_files, setlists, setlist_items) |
| V2 | feature_requests 테이블 |
| V3 | OCR 관련 컬럼 |
| V4 | songs.lyrics 컬럼 |
| V5 | feature_requests 추가 |
| V6 | songs.youtube_url 컬럼 |
| V7 | setlists.share_token 컬럼 |
| V8 | song_tags 테이블 |
| V9 | song_links 테이블 (기존 youtube_url 데이터 이전) |
| V10 | setlists.service_type 컬럼 제거 |
| V11 | users 테이블 (사용자 인증) |
| V12 | song_file_annotations 테이블 (악보 스타일러스 필기, #109) |
| V13 | setlist_items.performance_key 컬럼 (연주 키 메모, #75) |
| V14 | setlists·setlist_items.youtube_url 컬럼 |
| V15 | song_files.ocr_* 컬럼 제거 (OCR 기능 완전 제거, #142) |
| V16 | setlist_items 스테일 song_sheet_id 정리 (#154) |
| V17 | users.role 컬럼 (관리자 모드, #156) |
| V18 | song_sheets.sort_no 컬럼 (악보 버전 드래그 순서변경, #176) |

## 다음 작업 예정 이슈
> 이 표는 GitHub Open 이슈 기준. 완료된 이슈는 표에서 빠지고 `docs/worklog/`에 기록됨 — 새 세션은 시작 시 `gh`/GitHub MCP로 열린 이슈 목록을 다시 확인해 이 표가 최신인지 검증할 것.

| 번호 | 제목 |
|------|------|
| #144 | OCR을 Gemini API 기반으로 재도입 (Fly.io 마이크로서비스 대체) |
| #169 | [보안] 공개 가입 제한 - 초대코드 방식 도입 |
| #180 | 콘티 곡 추가 모달: 같은 곡 악보 여러 개 넘겨보기 + 악보 수정/삭제 (미리보기·인라인 수정은 구현됨, 삭제 기능만 남음) |

### 배포 정보
- 백엔드: OCI VM(`168.110.106.156`, `ubuntu` 계정) — 아래 "OCI 백엔드 운영" 참고.
- 프론트엔드: https://worship-sheet.vercel.app (Vercel, Git 연동으로 `main` push 시 자동배포, Root Directory: `frontend`)
- DB: Supabase
- OCR 자동 추출 기능은 사용 빈도 대비 상시 가동 비용 부담이 커서 #142로 완전히 제거함.
- ⚠️ 사용자 인증(#77) 관련 `JWT_SECRET`은 서버의 `.env.oci`에 설정(32바이트 이상 랜덤 문자열). 미설정 시 기본값(dev-only)이 사용되어 보안상 위험함
- **Fly.io는 OCI 이관 검증 완료 후 폐기함**(`fly.toml`, `.github/workflows/fly-deploy.yml` 저장소에서 제거). Fly.io 대시보드에서 앱 자체(`worship-sheet`, 이미 폐기 대상이던 `ocr-service` 포함) 삭제 및 결제 정리는 세션이 Fly 계정 접근 권한이 없어 사용자가 직접 진행 필요.

### OCI 백엔드 운영
- 백엔드는 OCI VM에서 Docker Compose로 상시 구동(DB는 Supabase, R2 스토리지도 그대로 사용).
- 이 세션(원격 격리 컨테이너)은 네트워크 정책상 SSH(raw TCP)를 못 나가서 서버에 직접 접속할 수 없음 — 배포는 전부 GitHub Actions(`OCI Deploy` 워크플로우, `.github/workflows/oci-deploy.yml`)가 SSH로 대신 수행하고, 최초 1회 서버 설정만 사람이 직접 함.
- **배포 방식**: `docker-compose.oci.yml`(백엔드 컨테이너 + `cloudflared` Quick Tunnel)을 `main` push마다 GitHub Actions가 SSH로 접속해 `git reset --hard origin/main` 후 `docker compose up -d --build`.
- **HTTPS 노출**: 도메인이 없어서 Cloudflare **Quick Tunnel**(`cloudflared tunnel --url ...`, 토큰/로그인 불필요) 사용을 사용자가 선택함. ⚠️ **Quick Tunnel 주소(`https://xxxx.trycloudflare.com`)는 배포(컨테이너 재시작)마다 바뀜** — 자동배포와는 궁합이 나쁘다는 점을 사용자에게 명시적으로 안내했고, 그래도 도메인 없이 진행하기로 확정함. 배포 후 Vercel의 `VITE_API_BASE_URL`을 새 주소로 **수동 갱신**해야 함(OCI Deploy 워크플로우 로그 마지막 줄에 새 주소가 출력됨).
  - 나중에 저렴한 도메인을 하나 사서 Cloudflare Named Tunnel로 바꾸면 주소가 고정되어 이 수동 갱신이 필요 없어짐(사용자에게 추천했으나 일단 보류).
- **필요한 GitHub 저장소 시크릿**(Settings → Secrets and variables → Actions): `OCI_HOST`(=`168.110.106.156`), `OCI_USER`(=`ubuntu`), `OCI_SSH_PRIVATE_KEY`(SSH 개인키 전체 내용). 세션은 이 값을 직접 다루지 않음 — 사용자가 GitHub UI로 직접 등록.
- **서버 최초 1회 수동 설정**(사람이 직접 SSH로 실행):
  ```bash
  curl -fsSL https://get.docker.com | sudo sh
  sudo usermod -aG docker $USER && newgrp docker
  git clone https://github.com/jeybell/sheet-music.git ~/sheet-music
  cd ~/sheet-music
  # .env.oci 에 Supabase(SPRING_DATASOURCE_*)·R2·JWT_SECRET·CORS_ALLOWED_ORIGINS 채워 넣기(.env.example 참고)
  docker compose --env-file .env.oci -f docker-compose.oci.yml up -d --build
  docker logs sheet-music-tunnel-oci   # 여기서 최초 Quick Tunnel 주소 확인
  ```
- 이후로는 `main` push → GitHub Actions가 알아서 재배포. 사람이 할 일은 배포 후 로그에서 새 Quick Tunnel 주소를 확인해 Vercel 환경변수만 갱신하는 것.
- **자동 생존 확인**(`OCI Keep Alive` 워크플로우, `.github/workflows/oci-keep-alive.yml`): 매일 1회 SSH로 접속해 현재 Quick Tunnel 주소로 실제 게스트 로그인 요청을 보내 Supabase 자동 일시정지·터널 idle 끊김을 예방한다. 실패하면 터널 컨테이너를 자동 재시작하고 새 주소를 로그에 남기는데, 이 경우엔 Vercel `VITE_API_BASE_URL`을 여전히 수동으로 갱신해야 함(Actions 탭에서 워크플로우 실행 로그 확인).

## 다음 세션 시작 가이드
- 새 세션 시작 시 **가장 먼저** `git pull origin main`으로 최신 코드를 받을 것.
- 진행 상황·결정사항이 바뀌면 CLAUDE.md를 즉시 업데이트하고 커밋·푸시할 것.

## 프론트엔드 주요 설계 (v24 이후)
- **테마**: 다크 기본, CSS 변수 기반 토큰 시스템 (`--background`, `--foreground`, `--card` 등)
- **FOUC 방지**: `index.html` 인라인 스크립트로 `dark` 클래스 선 적용
- **Tailwind v4**: `@custom-variant dark (&:where(.dark, .dark *))` + `@theme inline`
- **반응형**: `grid-cols-1 sm:grid-cols-2 lg:grid-cols-3`
- **곡 상세**: 악보 이미지 슬라이드 캐러셀 (sheets × files 플랫화), 키보드 ←/→ 네비게이션
- **로컬 개발 백엔드 전환**: 기본은 `npm run dev:local`(Vite `--mode localdev` → `frontend/.env.localdev`가 `http://localhost:8080`으로 설정, 로컬에서 JDK17로 직접 띄운 백엔드 사용: `JAVA_HOME=<jdk17 경로> ./gradlew bootRun`). 배포된 OCI 백엔드를 가리키고 싶으면 `frontend/.env.local`에 `VITE_API_BASE_URL`을 그 시점의 Cloudflare Quick Tunnel 주소로 직접 설정한 뒤 `npm run dev`(주소가 배포/재시작마다 바뀌므로 매번 최신값 확인 필요 — "OCI 백엔드 운영" 참고).

## 로컬 백엔드 실행
- **기본**: `JAVA_HOME=<jdk17 경로> ./gradlew bootRun` → 저장소 루트에 `.env`가 없으면 `application.yml` 기본값(로컬 Postgres, `localhost:5432/sheet_music`) 사용.
- **운영(Supabase) DB에 붙여서 실행하고 싶을 때**: 저장소 루트 `.env.example`을 `.env`로 복사(gitignore 처리, 커밋 안 됨) → `SPRING_DATASOURCE_URL`/`_USERNAME`/`_PASSWORD`를 Supabase 값으로 채움. `spring-dotenv` 라이브러리(`build.gradle`의 `developmentOnly` 의존성)가 기동 시 이 `.env`를 자동으로 읽어 반영하므로, Gradle `bootRun`/IntelliJ Run·Debug/jar 직접 실행 등 어떤 방식으로 띄워도 별도 설정 없이 동일하게 동작함(운영 배포용 jar에는 포함되지 않음):
  ```
  JAVA_HOME=<jdk17 경로> ./gradlew bootRun
  ```
  Docker Compose도 같은 `.env`를 쓰므로 파일 하나로 모든 실행 방식을 커버함.
  ⚠️ 운영 DB에 쓰기까지 그대로 반영됨(회원가입·곡 등록/삭제·콘티 편집 등). 순수 조회만 필요하면 대신 읽기전용 MCP(`.mcp.json`의 `supabase-db`, `SUPABASE_READONLY_URL`)를 사용할 것.
- **로컬 디버그 로깅(선택)**: `.env`에 `LOCAL_WEB_LOG_LEVEL=DEBUG`, `LOCAL_SQL_LOG_LEVEL=DEBUG`, `LOCAL_SQL_BIND_LOG_LEVEL=TRACE`를 추가하면 요청 로그(DispatcherServlet)와 실행되는 SQL/바인드 파라미터가 콘솔에 찍힘(`application.yml`에 고정 로거 이름 + 플레이스홀더 기본값 `WARN`/`INFO`로 선언되어 있어 운영에는 영향 없음). 실행 중인 프로세스는 `.env` 변경을 반영하지 않으므로 재시작 필요.
  ⚠️ Hibernate 로거 이름(`org.hibernate.SQL`)처럼 대소문자가 섞인 카테고리는 환경변수로 직접(`LOGGING_LEVEL_ORG_HIBERNATE_SQL=...`) 설정하면 Spring이 맵 키를 소문자로 접어버려 무시됨 — 반드시 `application.yml`에 고정 키로 선언하고 값만 환경변수로 주입할 것.
