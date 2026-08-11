# hint-generator v5 커밋 메시지 초안 (5개로 분할 권장)

> GitHub 웹에서 한 번에 업로드했다면 이 파일은 참고용이다.
> 이후 IntelliJ에서 수정할 때 커밋 단위/문구의 기준으로 쓴다.

브랜치: feature/hintGenerator_v5 (origin/master 기준)
주의: `git add .` 금지. 아래처럼 파일을 지정해서 add.

## 1) 도메인 모델 + 라운드 규칙
```
git add shared/src/jvmMain/kotlin/kr/ac/sunmoon/hunminjeongeum_server/hint/Models.kt \
        shared/src/jvmMain/kotlin/kr/ac/sunmoon/hunminjeongeum_server/hint/RoundPlan.kt \
        shared/src/jvmMain/kotlin/kr/ac/sunmoon/hunminjeongeum_server/hint/Hangul.kt
git commit -m "feat(hint): v5 도메인 모델·라운드 규칙 추가

- WordEntry/RoundHints를 DB 스키마(id:Int, quizCategory:Int, wordQuiz)에 정합
- 초성힌트 필드를 팀 합의 변수명 easyWordHint/normalWordHint로 통일
- QuizCategory ID 매핑 확정(과일1/나라2/음식3/동물4/사자성어5), '요리'는 '음식' 별칭
- 힌트 길이 상수를 RoundPlan.HintLength로 통합(세 난이도 2~20자)"
```

## 2) 검증기 + 선택기
```
git add shared/src/jvmMain/kotlin/kr/ac/sunmoon/hunminjeongeum_server/hint/HintValidator.kt \
        shared/src/jvmMain/kotlin/kr/ac/sunmoon/hunminjeongeum_server/hint/HintSelector.kt \
        shared/src/jvmMain/kotlin/kr/ac/sunmoon/hunminjeongeum_server/hint/RevealPlanner.kt
git commit -m "feat(hint): v5 검증기 길이 기준을 2~20자로 통일

- 프롬프트와 검증기의 길이 불일치 해소(easy 예시 11자가 통과하도록)
- 검증기는 최소 방어선만 담당, 세부 품질은 프롬프트가 담당
- validateWord가 카테고리 ID 유효성까지 확인"
```

## 3) AI 생성기 + 프롬프트 v5
```
git add shared/src/jvmMain/kotlin/kr/ac/sunmoon/hunminjeongeum_server/hint/FeatureHintGenerator.kt \
        shared/src/jvmMain/kotlin/kr/ac/sunmoon/hunminjeongeum_server/hint/OpenAiDto.kt \
        shared/src/jvmMain/kotlin/kr/ac/sunmoon/hunminjeongeum_server/hint/Prompt.kt \
        shared/src/jvmMain/kotlin/kr/ac/sunmoon/hunminjeongeum_server/hint/OpenAiHintGenerator.kt
git commit -m "feat(hint): 프롬프트 v5(본문 무수정) + OpenAI 생성기

- PROMPT_VERSION=v5, 변경 이력 추가(SYSTEM/사용자 메시지 본문은 v4 그대로)
- 카테고리 이름 참조를 entry.categoryLabel로 교체(DB가 숫자 ID를 주므로)
- OpenAiHintGenerator는 CIO 엔진 의존으로 jvmMain에 배치"
```

## 4) 저장소 + 서비스 + 폴백 + IO
```
git add shared/src/jvmMain/kotlin/kr/ac/sunmoon/hunminjeongeum_server/hint/WordRepository.kt \
        shared/src/jvmMain/kotlin/kr/ac/sunmoon/hunminjeongeum_server/hint/SupabaseWordRepository.kt \
        shared/src/jvmMain/kotlin/kr/ac/sunmoon/hunminjeongeum_server/hint/HintService.kt \
        shared/src/jvmMain/kotlin/kr/ac/sunmoon/hunminjeongeum_server/hint/FallbackHintRepository.kt \
        shared/src/jvmMain/kotlin/kr/ac/sunmoon/hunminjeongeum_server/hint/HintJson.kt \
        shared/src/jvmMain/resources/hint/words_sample.json \
        shared/src/jvmMain/resources/hint/fallback_hints.json
git commit -m "feat(hint): 단어 저장소 인터페이스 + Supabase 어댑터 + 폴백

- WordRepository(exclude:Set<Int>), JSON/DB 두 구현
- SupabaseWordRepository: 팀 QuizWordRepository만 감싸고 SDK 타입은 직접 안 씀
- 폴백 힌트를 v5 길이(20자 이내)로 재작성
- words_sample.json(48개, 1~5음절×5카테고리), 직렬화는 HintJson으로 캡슐화"
```

## 5) 테스트 + 진입점
```
git add shared/src/jvmTest/kotlin/kr/ac/sunmoon/hunminjeongeum_server/hint/HangulConsistencyTest.kt \
        desktopApp/src/main/kotlin/kr/ac/sunmoon/hunminjeongeum_server/HintDemoMain.kt
git commit -m "test(hint): Hangul↔KoreanInitial 일치 테스트 + 데모 진입점

- 두 초성 로직이 어긋나면 빌드가 깨지도록 회귀 테스트 추가
- HintDemoMain(파일명 충돌 회피): --fake/--audit/--db 모드
- desktopApp에 serialization 의존성이 없어도 컴파일되도록 IO를 HintJson에 위임"
```

## 푸시
```
git push -u origin feature/hintGenerator_v5
```
