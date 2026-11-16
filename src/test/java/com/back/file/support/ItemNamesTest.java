package com.back.file.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.text.Normalizer;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ItemNamesTest {

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "a/b", "a\\b", "a\tb", "a\u0000b", "a\u007Fb"})
    @DisplayName("NAME-U1: 빈 이름, 공백만, 슬래시·역슬래시·제어 문자는 위반")
    void invalidCharacters(String name) {
        assertThat(ItemNames.isValid(name)).isFalse();
    }

    @Test
    @DisplayName("NAME-U1: 256자는 위반, NAME-U2: 1자와 255자는 통과")
    void length() {
        assertThat(ItemNames.isValid("a".repeat(256))).isFalse();
        assertThat(ItemNames.isValid("a".repeat(255))).isTrue();
        assertThat(ItemNames.isValid("가".repeat(255))).isTrue();
        assertThat(ItemNames.isValid("a")).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"a b.c.txt", "한글 이름.pdf", ".env", "a..b"})
    @DisplayName("NAME-U2: 한글·공백·점이 들어간 이름은 통과")
    void validNames(String name) {
        assertThat(ItemNames.isValid(name)).isTrue();
    }

    @Test
    @DisplayName("NAME-U3: NFD 한글은 NFC와 같은 키(소문자)가 된다")
    void normalization() {
        String nfd = Normalizer.normalize("한글.TXT", Normalizer.Form.NFD);
        assertThat(nfd).isNotEqualTo("한글.TXT");
        assertThat(ItemNames.key(nfd)).isEqualTo(ItemNames.key("한글.txt")).isEqualTo("한글.txt");
        assertThat(ItemNames.normalize(nfd)).isEqualTo("한글.TXT");
    }

    @Test
    @DisplayName("NAME-U4: a.pdf, a (1).pdf가 있으면 a (2).pdf")
    void numbering() {
        assertThat(ItemNames.available("a.pdf", Set.of("a.pdf", "a (1).pdf"))).isEqualTo("a (2).pdf");
        assertThat(ItemNames.available("a.pdf", Set.of())).isEqualTo("a.pdf");
        assertThat(ItemNames.available("A.PDF", Set.of("a.pdf"))).isEqualTo("A (1).PDF");
    }

    @Test
    @DisplayName("NAME-U5: 확장자 없음 / 점으로 시작 / 확장자 두 개")
    void numberingWithoutExtension() {
        assertThat(ItemNames.available("a", Set.of("a"))).isEqualTo("a (1)");
        assertThat(ItemNames.available(".env", Set.of(".env"))).isEqualTo(".env (1)");
        assertThat(ItemNames.available("a.tar.gz", Set.of("a.tar.gz"))).isEqualTo("a.tar (1).gz");
    }

    @Test
    @DisplayName("번호를 붙여 255자를 넘으면 확장자 앞을 줄인다")
    void numberingKeepsLimit() {
        String name = "a".repeat(251) + ".pdf";
        String numbered = ItemNames.available(name, Set.of(ItemNames.key(name)));
        assertThat(numbered).hasSize(255).endsWith(" (1).pdf");
        assertThat(ItemNames.isValid(numbered)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"CON", "con", "con.txt", "LPT1.pdf", "COM9", "nul.tar.gz", "Aux", "prn.doc"})
    @DisplayName("NAME-U6: Windows 예약어는 확장자가 붙어도 위반")
    void reservedNames(String name) {
        assertThat(ItemNames.isValid(name)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"CONSOLE.txt", "COM10", "xcon.txt", "con_1.txt", "LPT0"})
    @DisplayName("NAME-U7: 예약어와 비슷하지만 다른 이름은 통과")
    void notReserved(String name) {
        assertThat(ItemNames.isValid(name)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"a.", "a ", "a.txt.", "폴더 "})
    @DisplayName("NAME-U8: 끝의 점·공백은 위반")
    void trailingDotOrSpace(String name) {
        assertThat(ItemNames.isValid(name)).isFalse();
    }
}
