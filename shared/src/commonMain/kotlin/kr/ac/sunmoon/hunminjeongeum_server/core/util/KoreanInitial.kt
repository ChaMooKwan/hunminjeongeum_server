package kr.ac.sunmoon.hunminjeongeum_server.core.util

object KoreanInitial {
    private val initials = charArrayOf(
        'ㄱ', 'ㄲ', 'ㄴ', 'ㄷ', 'ㄸ', 'ㄹ', 'ㅁ',
        'ㅂ', 'ㅃ', 'ㅅ', 'ㅆ', 'ㅇ', 'ㅈ', 'ㅉ',
        'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ'
    )

    // 단어를 초성으로 반환해주는 식
    fun makeInitials(text: String): String {
        return buildString {
            text.trim().forEach { character ->
                when {
                    character in '가'..'힣' -> {
                        val index =
                            (character.code - '가'.code) / (21 * 28)

                        append(initials[index])
                    }

                    character.isLetterOrDigit() -> {
                        append(character)
                    }
                }
            }
        }
    }
}
