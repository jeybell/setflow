package com.jeybell.sheetmusic.setlist;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class SetlistTextParserTest {

    @Test
    void 제목줄에서_날짜와_제목을_추출한다() {
        var result = SetlistTextParser.parse("<2026.9.2.(수)저녁 만나예배>\n・목마른 예배자 F");

        assertThat(result.serviceDate()).isEqualTo(LocalDate.of(2026, 9, 2));
        assertThat(result.title()).isEqualTo("2026.9.2.(수)저녁 만나예배");
    }

    @Test
    void 실제_콘티_텍스트를_순서대로_파싱한다() {
        String text = """
                <2026.9.2.(수)저녁 만나예배>
                ・(Intro)목마른 예배자 F
                  +(후렴만)온 맘 다해 F
                ・(Intro)살아계신 주 G
                  +주 안에 있는 나에게 G
                ・(Intro)주님을 예배하는 것 A
                  +(후렴만)주 임재 안에서 A
                ・(Intro)주품에 D
                """;

        var result = SetlistTextParser.parse(text);

        assertThat(result.items()).hasSize(7);
        assertThat(result.items().get(0)).isEqualTo(
                new SetlistTextParser.ParsedItem("목마른 예배자", "F", "(Intro)"));
        assertThat(result.items().get(1)).isEqualTo(
                new SetlistTextParser.ParsedItem("온 맘 다해", "F", "(후렴만)"));
        assertThat(result.items().get(3)).isEqualTo(
                new SetlistTextParser.ParsedItem("주 안에 있는 나에게", "G", null));
        assertThat(result.items().get(6)).isEqualTo(
                new SetlistTextParser.ParsedItem("주품에", "D", "(Intro)"));
    }

    @Test
    void 끝에_키가_없는_줄은_제목만_남는다() {
        var result = SetlistTextParser.parse("<2026.8.30.(주일)오전 예배>\n・(폐회송)말씀 앞에서 세상 앞에서");

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).rawTitle()).isEqualTo("말씀 앞에서 세상 앞에서");
        assertThat(result.items().get(0).performanceKey()).isNull();
        assertThat(result.items().get(0).memo()).isEqualTo("(폐회송)");
    }

    @Test
    void 제목줄이_없으면_날짜와_제목이_null이다() {
        var result = SetlistTextParser.parse("목마른 예배자 F");

        assertThat(result.serviceDate()).isNull();
        assertThat(result.title()).isNull();
        assertThat(result.items()).hasSize(1);
    }

    @Test
    void 빈_텍스트는_곡_목록이_비어있다() {
        var result = SetlistTextParser.parse("   \n\n  ");

        assertThat(result.items()).isEmpty();
    }
}
