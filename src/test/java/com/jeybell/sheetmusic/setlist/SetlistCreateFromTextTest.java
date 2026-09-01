package com.jeybell.sheetmusic.setlist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jeybell.sheetmusic.setlist.dto.SetlistResponse;
import com.jeybell.sheetmusic.song.Song;
import com.jeybell.sheetmusic.song.SongSheet;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/**
 * 텍스트 붙여넣기로 콘티를 한 번에 생성하는 기능(#230 후속) 검증.
 */
@DataJpaTest
@Import(SetlistService.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:setlist-from-text;DB_CLOSE_DELAY=-1",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
class SetlistCreateFromTextTest {

    @Autowired
    private EntityManager em;
    @Autowired
    private SetlistService service;

    private Song song(String title) {
        Song s = new Song(title, null, null, null, null);
        em.persist(s);
        return s;
    }

    private void sheet(Song song, String key) {
        SongSheet sh = new SongSheet(key, null, null);
        song.addSheet(sh);
        em.persist(sh);
    }

    @Test
    void 텍스트로_콘티와_곡_순서_키_메모가_모두_생성된다() {
        Song a = song("목마른 예배자");
        sheet(a, "F");
        Song b = song("온 맘 다해");
        sheet(b, "F");
        Song c = song("주품에");
        // c 는 등록된 악보 버전이 없음 → performanceKey 는 저장돼도 songSheetId 는 null 이어야 함
        em.flush();
        em.clear();

        String text = """
                <2026.9.2.(수)저녁 만나예배>
                ・(Intro)목마른 예배자 F
                  +(후렴만)온 맘 다해 F
                ・(Intro)주품에 D
                """;

        SetlistResponse response = service.createFromText(text);

        assertThat(response.serviceDate()).isEqualTo(LocalDate.of(2026, 9, 2));
        assertThat(response.title()).isEqualTo("2026.9.2.(수)저녁 만나예배");
        assertThat(response.items()).hasSize(3);

        assertThat(response.items().get(0).songTitle()).isEqualTo("목마른 예배자");
        assertThat(response.items().get(0).performanceKey()).isEqualTo("F");
        assertThat(response.items().get(0).memo()).isEqualTo("(Intro)");
        assertThat(response.items().get(0).songSheetId()).isNotNull();

        assertThat(response.items().get(1).songTitle()).isEqualTo("온 맘 다해");
        assertThat(response.items().get(1).memo()).isEqualTo("(후렴만)");

        assertThat(response.items().get(2).songTitle()).isEqualTo("주품에");
        assertThat(response.items().get(2).performanceKey()).isEqualTo("D");
        assertThat(response.items().get(2).songSheetId()).isNull();
    }

    @Test
    void 일치하는_곡이_없으면_아무것도_생성하지_않고_예외() {
        song("목마른 예배자");
        em.flush();
        em.clear();

        String text = """
                <2026.9.2.(수)저녁 만나예배>
                ・존재하지 않는 곡 F
                """;

        assertThatThrownBy(() -> service.createFromText(text))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("존재하지 않는 곡")
                .hasMessageContaining("찾을 수 없습니다");
    }

    @Test
    void 동일제목_곡이_중복이면_예외() {
        song("은혜");
        song("은혜");
        em.flush();
        em.clear();

        String text = """
                <2026.9.2.(수)저녁 만나예배>
                ・은혜 G
                """;

        assertThatThrownBy(() -> service.createFromText(text))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2개");
    }

    @Test
    void 날짜를_인식하지_못하면_예외() {
        assertThatThrownBy(() -> service.createFromText("목마른 예배자 F"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("날짜");
    }
}
