package com.jeybell.sheetmusic.song;

import com.jeybell.sheetmusic.song.dto.PopularSongResponse;
import com.jeybell.sheetmusic.song.dto.SongSetlistHistoryResponse;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SongRepository extends JpaRepository<Song, Long> {

    /**
     * 곡 검색(페이지네이션). 컬렉션(sheets) fetch join 을 쓰지 않아 DB 레벨 페이징이 가능하다.
     * 정렬은 Pageable 의 Sort 로 전달한다. sheets 는 SongResponse 매핑 시 지연 로딩된다.
     */
    @Query("""
            select s
            from Song s
            where s.deletedAt is null
              and (:query is null
                or lower(s.title) like :query
                or lower(s.artist) like :query
                or lower(s.lyrics) like :query
                or exists (
                  select 1 from Song stq join stq.tags tq
                  where stq = s and lower(tq) like :query
                ))
              and (:songKey is null
                or exists (
                  select 1 from SongSheet ss
                  where ss.song = s
                    and ss.deletedAt is null
                    and lower(ss.sheetKey) like :songKey
                ))
              and (:tagCount = 0
                or (
                  select count(distinct t) from Song sx join sx.tags t
                  where sx = s and t in :tags
                ) = :tagCount)
            """)
    Page<Song> searchSongs(@Param("query") String query, @Param("songKey") String songKey,
                           @Param("tags") List<String> tags, @Param("tagCount") long tagCount,
                           Pageable pageable);

    /**
     * 키순 정렬 전용. 곡의 대표 키(활성 악보 중 최소 sheetKey)로 오름차순 정렬한다.
     * 키가 없는 곡은 마지막에 온다. 필터 조건은 searchSongs 와 동일.
     * 정렬을 쿼리에 고정하므로 Pageable 의 Sort 는 무시(unsorted)로 전달할 것.
     */
    @Query("""
            select s
            from Song s
            where s.deletedAt is null
              and (:query is null
                or lower(s.title) like :query
                or lower(s.artist) like :query
                or lower(s.lyrics) like :query
                or exists (
                  select 1 from Song stq join stq.tags tq
                  where stq = s and lower(tq) like :query
                ))
              and (:songKey is null
                or exists (
                  select 1 from SongSheet ss
                  where ss.song = s
                    and ss.deletedAt is null
                    and lower(ss.sheetKey) like :songKey
                ))
              and (:tagCount = 0
                or (
                  select count(distinct t) from Song sx join sx.tags t
                  where sx = s and t in :tags
                ) = :tagCount)
            order by (
              select min(ss2.sheetKey) from SongSheet ss2
              where ss2.song = s and ss2.deletedAt is null
            ) asc nulls last
            """)
    Page<Song> searchSongsOrderByKey(@Param("query") String query, @Param("songKey") String songKey,
                                     @Param("tags") List<String> tags, @Param("tagCount") long tagCount,
                                     Pageable pageable);

    /**
     * 최근 사용일순 정렬 전용. 콘티(setlist)에 쓰인 적 있는 곡은 최근 사용일 내림차순,
     * 쓰인 적 없는 곡은 마지막에 온다. 필터 조건은 searchSongs 와 동일.
     */
    @Query("""
            select s
            from Song s
            where s.deletedAt is null
              and (:query is null
                or lower(s.title) like :query
                or lower(s.artist) like :query
                or lower(s.lyrics) like :query
                or exists (
                  select 1 from Song stq join stq.tags tq
                  where stq = s and lower(tq) like :query
                ))
              and (:songKey is null
                or exists (
                  select 1 from SongSheet ss
                  where ss.song = s
                    and ss.deletedAt is null
                    and lower(ss.sheetKey) like :songKey
                ))
              and (:tagCount = 0
                or (
                  select count(distinct t) from Song sx join sx.tags t
                  where sx = s and t in :tags
                ) = :tagCount)
            order by (
              select max(sl.serviceDate) from SetlistItem si join si.setlist sl
              where si.song = s and sl.deletedAt is null
            ) desc nulls last
            """)
    Page<Song> searchSongsOrderByLastUsed(@Param("query") String query, @Param("songKey") String songKey,
                                          @Param("tags") List<String> tags, @Param("tagCount") long tagCount,
                                          Pageable pageable);

    /**
     * 곡 상세: 해당 곡이 포함된 셋리스트 이력(최근순). soft-delete 된 콘티는 제외.
     */
    @Query("""
            select new com.jeybell.sheetmusic.song.dto.SongSetlistHistoryResponse(
                sl.setlistId, sl.serviceDate, sl.title)
            from SetlistItem si join si.setlist sl
            where si.song.songId = :songId and sl.deletedAt is null
            order by sl.serviceDate desc
            """)
    List<SongSetlistHistoryResponse> findSetlistHistoryForSong(@Param("songId") Long songId);

    @Query("select distinct t from Song s join s.tags t where s.deletedAt is null order by t")
    List<String> findAllTags();

    /**
     * 자주 쓰는 곡: 활성 콘티(setlist)에 담긴 setlist_items 참조 횟수 기준 내림차순.
     */
    @Query("""
            select new com.jeybell.sheetmusic.song.dto.PopularSongResponse(
                s.songId, s.title, s.artist, count(i))
            from SetlistItem i
              join i.song s
              join i.setlist sl
            where s.deletedAt is null and sl.deletedAt is null
            group by s.songId, s.title, s.artist
            order by count(i) desc
            """)
    List<PopularSongResponse> findPopularSongs(Pageable pageable);

    @Query("""
            select distinct s
            from Song s
            left join fetch s.sheets sheets
            where s.songId = :songId
              and s.deletedAt is null
            """)
    Optional<Song> findActiveBySongIdWithSheets(@Param("songId") Long songId);

    /** 관리자 휴지통: soft delete 된 곡을 최근 삭제순으로. */
    List<Song> findByDeletedAtIsNotNullOrderByDeletedAtDesc();

    @Query("""
            select count(s) > 0
            from Song s
            where trim(lower(s.title)) = trim(lower(:title))
              and s.deletedAt is null
              and (:excludeId is null or s.songId <> :excludeId)
            """)
    boolean existsByTitleIgnoreCase(@Param("title") String title, @Param("excludeId") Long excludeId);

    /**
     * 텍스트로 콘티 생성 시 곡 제목 매칭용. 정확히 일치하는 곡이 여러 개(중복 곡)일 수 있어
     * List 로 반환 — 호출 측에서 0개(미발견)/2개 이상(모호함)을 구분해 처리한다.
     */
    @Query("""
            select distinct s
            from Song s
            left join fetch s.sheets sheets
            where s.deletedAt is null
              and trim(lower(s.title)) = trim(lower(:title))
            """)
    List<Song> findActiveByTitleIgnoreCase(@Param("title") String title);
}
