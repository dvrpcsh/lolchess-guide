package com.lolchess.util;

/**
 * [역할] 단어의 마지막 글자 받침 유무에 따라 한국어 조사(을/를, 과/와 등)를 골라 주는 유틸리티.
 *
 * [Data Flow]
 *   ActionableGuideService가 "[B.F. 대검]과", "[드레이븐]을" 같은 가이드 문장을 만들 때 사용
 *   (아이템/챔피언 이름은 Data Dragon 데이터라 받침 유무를 미리 알 수 없다)
 */
public final class KoreanJosa {

    private static final char HANGUL_START = '가';
    private static final char HANGUL_END = '힣';
    private static final int FINAL_CONSONANT_COUNT = 28;

    private KoreanJosa() {
    }

    /**
     * @param word         조사를 붙일 단어
     * @param withFinal    받침이 있을 때 조사 (예: "을", "과")
     * @param withoutFinal 받침이 없을 때 조사 (예: "를", "와")
     * @return 단어 마지막 글자가 한글이 아니면(영문/숫자 등) withFinal을 기본으로 사용
     */
    public static String pick(String word, String withFinal, String withoutFinal) {
        if (word == null || word.isEmpty()) {
            return withFinal;
        }
        char last = word.charAt(word.length() - 1);
        if (last < HANGUL_START || last > HANGUL_END) {
            return withFinal;
        }
        return (last - HANGUL_START) % FINAL_CONSONANT_COUNT == 0 ? withoutFinal : withFinal;
    }

    /** "[단어]을" / "[단어]를" */
    public static String objectOf(String word) {
        return "[" + word + "]" + pick(word, "을", "를");
    }

    /** "[단어]과" / "[단어]와" */
    public static String andOf(String word) {
        return "[" + word + "]" + pick(word, "과", "와");
    }
}
