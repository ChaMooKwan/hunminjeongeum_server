# hint-generator v5 — 산출물 안내

## ⚠️ 이건 저장소가 아니라 "추가할 파일들"입니다

이 폴더 안의 `shared/` 와 `desktopApp/` 는 **이미 프로젝트에 있는 폴더**입니다.
여기 담긴 건 그 안에 새로 추가할 파일들뿐입니다. 저장소를 덮어쓰는 게 아닙니다.

각 파일의 경로가 곧 "프로젝트의 어디에 놓을지"입니다. 예:
`shared/src/jvmMain/kotlin/kr/ac/sunmoon/hunminjeongeum_server/hint/Models.kt`
→ 프로젝트의 같은 경로에 그대로 넣으면 됩니다. (모두 새 파일이라 기존 파일을 건드리지 않음)

## 추가되는 파일 (19개, 전부 신규)

> v5 배치 메모: 로직을 `commonMain`이 아니라 **`jvmMain`** 에 둔다.
> `WordRepository`/`FallbackHintRepository` 가 `java.io.File` 을 쓰는데
> `commonMain` 은 JVM 외 타깃도 컴파일되므로 `java.*` 를 참조할 수 없기 때문.

| 위치 | 개수 | 내용 |
|---|---|---|
| shared/src/jvmMain/.../hint/ | 15 | 로직 전체 (Room.kt에서 HintService 사용 가능) |
| shared/src/jvmMain/resources/hint/ | 2 | words_sample.json(48개), fallback_hints.json(9개) |
| shared/src/jvmTest/.../hint/ | 1 | HangulConsistencyTest |
| desktopApp/.../hint/ | 1 | HintDemoMain.kt (확인용 진입점) |

**gradle 파일은 한 줄도 수정하지 않습니다.** 새 파일만 추가하므로 다른 팀원과 충돌 없음.

## 실행 (IntelliJ)

HintDemoMain.kt 의 main 왼쪽 ▶ → Run. 인자는 Run > Edit Configurations:
- Program arguments: `--fake --rounds=6` (키 없이 흐름 확인)
- Program arguments: `--audit` + Environment: `OPENAI_API_KEY=sk-...` (실패율 측정)
- Program arguments: `--db --rounds=5 --category=나라` (실제 Supabase 사용)

## 커밋 (파일 지정, git add . 금지)

`commit-messages-v5.md` 참조. 브랜치가 origin/master 기준이면 개행 문제 없음.
