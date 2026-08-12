# Hunminjeongeum Server

훈민정음 초성 퀴즈 게임의 서버 및 데이터 도구 프로젝트입니다.

Compose Multiplatform Desktop 앱으로 서버를 실행하고, 클라이언트들이 TCP Socket으로 접속해 실시간 대기실, 채팅, 문제 출제, 점수 동기화, 타이머, 힌트 수신을 함께 처리합니다. 퀴즈 단어는 Supabase에서 가져오며, 일부 도구는 외부 API와 OpenAI API를 사용해 단어/힌트 데이터를 생성하거나 검증합니다.

## 주요 기능

- TCP Socket 기반 멀티플레이 서버
- 최대 접속 인원 제한 및 대기실 사용자 목록 브로드캐스트
- 게임 시작, 라운드 문제 출제, 정답 판정, 점수 갱신
- 전체 게임 타이머와 라운드별 힌트 타이머
- Supabase 기반 퀴즈 단어 조회
- 과일, 음식, 국가, 동물, 사자성어 등 카테고리별 시더 및 테스트 도구
- OpenAI API를 이용한 특징 힌트 생성 파이프라인

## 기술 스택

- Kotlin 2.4.10
- Kotlin Multiplatform JVM
- Compose Multiplatform Desktop
- Ktor Client
- Kotlinx Serialization
- Supabase Postgrest
- Gradle Wrapper

## 프로젝트 구조

```text
.
├── desktopApp/
│   └── src/main/kotlin/.../main.kt          # 데스크톱 서버 앱 진입점
├── shared/
│   └── src/commonMain/kotlin/.../
│       ├── App.kt                           # 포트 입력 UI 및 서버 시작
│       ├── logic/                           # 서버 연결, 방, 게임 상태, 메시지 모델
│       ├── hint/                            # 힌트 생성, 선택, 검증, fallback 로직
│       ├── data/supabase/                   # Supabase 클라이언트 및 단어 저장소
│       ├── data/remote/                     # 외부 API 클라이언트
│       └── core/util/                       # 한글 초성 처리 유틸
├── docs/
│   └── hint-generator-v5/                   # 힌트 생성 관련 문서
└── gradle/
```

## 실행 방법

Windows PowerShell 기준:

```powershell
.\gradlew.bat :desktopApp:run
```

macOS/Linux 기준:

```bash
./gradlew :desktopApp:run
```

앱이 실행되면 사용할 포트 번호를 입력하고 확인 버튼을 누릅니다. 클라이언트 앱에서는 같은 IP 주소와 포트 번호로 접속하면 됩니다.

## 힌트 데모 실행

API 키 없이 흐름만 확인하려면 fake 모드를 사용할 수 있습니다.

```powershell
.\gradlew.bat :desktopApp:run -DmainClass=kr.ac.sunmoon.hunminjeongeum_server.hint.HintDemoMainKt --args="--fake --rounds=6"
```

실제 OpenAI API를 사용할 때는 환경변수를 설정합니다.

```powershell
$env:OPENAI_API_KEY="sk-..."
$env:OPENAI_MODEL="gpt-4.1-nano"
.\gradlew.bat :desktopApp:run -DmainClass=kr.ac.sunmoon.hunminjeongeum_server.hint.HintDemoMainKt
```

## 테스트

```powershell
.\gradlew.bat test
```

특정 모듈만 확인하려면 다음처럼 실행할 수 있습니다.

```powershell
.\gradlew.bat :shared:jvmTest
```

## 환경변수

- `OPENAI_API_KEY`: 실제 AI 힌트 생성을 사용할 때 필요합니다.
- `OPENAI_MODEL`: 사용할 OpenAI 모델명입니다. 설정하지 않으면 코드의 기본값을 사용합니다.
- `FOOD_API_KEY`: 음식 데이터 API 테스트 또는 시더 실행 시 필요합니다.

## 클라이언트 연동 흐름

1. 서버 앱을 실행하고 포트를 입력합니다.
2. 클라이언트 앱에서 사용자 이름, 서버 IP, 포트를 입력합니다.
3. 클라이언트가 접속하면 서버가 사용자 목록을 브로드캐스트합니다.
4. 대기실에서 카테고리를 선택하고 게임을 시작합니다.
5. 서버가 문제, 타이머, 힌트, 점수, 게임 종료 메시지를 각 클라이언트로 전송합니다.

## 참고

- 서버 프로토콜은 문자열 명령 기반입니다. 예: `/startGame,`, `/chat,`, `/question,`, `/score,`, `/timer,`, `/hint^`, `/gameOver,`
- Supabase 연결 정보는 `SupabaseClientProvider`에서 관리됩니다.
- 콘솔 출력 한글 깨짐을 줄이기 위해 데스크톱 앱 실행 태스크에 UTF-8 JVM 옵션과 콘솔 에이전트 설정이 포함되어 있습니다.
