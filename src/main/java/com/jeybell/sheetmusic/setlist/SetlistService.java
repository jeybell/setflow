package com.jeybell.sheetmusic.setlist;

import com.jeybell.sheetmusic.global.exception.ResourceNotFoundException;
import com.jeybell.sheetmusic.setlist.dto.SetlistListRow;
import com.jeybell.sheetmusic.setlist.dto.SetlistRequest;
import com.jeybell.sheetmusic.setlist.dto.SetlistResponse;
import com.jeybell.sheetmusic.setlist.dto.SharedSetlistResponse;
import com.jeybell.sheetmusic.song.Song;
import com.jeybell.sheetmusic.song.SongRepository;
import com.jeybell.sheetmusic.song.SongSheet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SetlistService {

    private final SetlistRepository setlistRepository;
    private final SongRepository songRepository;

    public SetlistService(SetlistRepository setlistRepository, SongRepository songRepository) {
        this.setlistRepository = setlistRepository;
        this.songRepository = songRepository;
    }

    public List<SetlistResponse> getSetlists() {
        Map<Long, List<SetlistListRow>> grouped = new LinkedHashMap<>();
        for (SetlistListRow row : setlistRepository.findAllActiveForList()) {
            grouped.computeIfAbsent(row.setlistId(), key -> new ArrayList<>()).add(row);
        }
        return grouped.values().stream()
                .map(SetlistResponse::fromRows)
                .toList();
    }

    @Transactional
    public SetlistResponse createSetlist(SetlistRequest request) {
        Setlist setlist = new Setlist(
                request.serviceDate(),
                request.title(),
                request.memo(),
                request.youtubeUrl()
        );
        return SetlistResponse.from(setlistRepository.save(setlist));
    }

    public SetlistResponse getSetlist(Long setlistId) {
        return SetlistResponse.from(getActive(setlistId));
    }

    @Transactional
    public SetlistResponse updateSetlist(Long setlistId, SetlistRequest request) {
        Setlist setlist = getActive(setlistId);
        setlist.update(
                request.serviceDate(),
                request.title(),
                request.memo(),
                request.youtubeUrl()
        );
        return SetlistResponse.from(setlist);
    }

    @Transactional
    public void deleteSetlist(Long setlistId) {
        Setlist setlist = getActive(setlistId);
        setlist.softDelete();
    }

    /**
     * 콘티 복사(템플릿으로 재사용). 곡 순서·악보 버전 설정을 그대로 복사하고
     * 날짜만 새로 지정한다. 공유 링크는 복사하지 않는다.
     */
    @Transactional
    public SetlistResponse duplicateSetlist(Long setlistId, LocalDate newServiceDate) {
        Setlist source = getActive(setlistId);
        Setlist copy = new Setlist(newServiceDate, source.getTitle(), source.getMemo(), source.getYoutubeUrl());
        for (SetlistItem item : source.getItems()) {
            copy.addItem(new SetlistItem(item.getSong(), item.getSongSheet(), item.getOrderNo(),
                    item.getMemo(), item.getPerformanceKey(), item.getYoutubeUrl()));
        }
        return SetlistResponse.from(setlistRepository.save(copy));
    }

    /**
     * 텍스트(제목 줄 + 곡 목록)를 파싱해 콘티를 한 번에 생성한다. 각 줄의 곡 제목은 먼저 기존에
     * 등록된 곡과 정확히 일치(대소문자·공백 무시)하는지 찾고, 없으면 제목을 포함하는 곡으로
     * 완화해서 찾는다. 그래도 없으면 해당 제목만으로 곡을 새로 등록해 이어서 진행한다. 일치하는
     * 곡이 여러 개(중복 제목 등)면 그중 가장 먼저 등록된 곡을 사용한다 — 실패하지 않는다.
     */
    @Transactional
    public SetlistResponse createFromText(String text) {
        SetlistTextParser.ParsedSetlist parsed = SetlistTextParser.parse(text);
        if (parsed.items().isEmpty()) {
            throw new IllegalArgumentException("텍스트에서 곡을 찾지 못했습니다.");
        }
        if (parsed.serviceDate() == null) {
            throw new IllegalArgumentException(
                    "날짜를 인식하지 못했습니다. 첫 줄이 <2026.9.2.(수)저녁 만나예배> 같은 형식인지 확인해주세요.");
        }

        Setlist setlist = new Setlist(parsed.serviceDate(), parsed.title(), null);
        int orderNo = 1;
        for (SetlistTextParser.ParsedItem parsedItem : parsed.items()) {
            Song song = findOrCreateMatchingSong(parsedItem.rawTitle());

            SongSheet matchedSheet = parsedItem.performanceKey() == null ? null
                    : song.getSheets().stream()
                            .filter(sheet -> sheet.getDeletedAt() == null)
                            .filter(sheet -> parsedItem.performanceKey().equalsIgnoreCase(sheet.getSheetKey()))
                            .findFirst()
                            .orElse(null);

            setlist.addItem(new SetlistItem(
                    song, matchedSheet, orderNo++, parsedItem.memo(), parsedItem.performanceKey(), null));
        }

        return SetlistResponse.from(setlistRepository.save(setlist));
    }

    /**
     * rawTitle 에 해당하는 곡을 정확일치 → 부분일치(포함) 순으로 찾아 반환한다. 일치하는 곡이
     * 여러 개면(동일 제목 중복 등록 등) 가장 먼저 등록된 곡(songId 오름차순 첫 번째)을 사용한다.
     * 그래도 없으면 rawTitle 만으로 곡을 새로 등록해 반환한다 — 항상 곡을 반환하며 실패하지 않는다.
     */
    private Song findOrCreateMatchingSong(String rawTitle) {
        List<Song> exactMatches = songRepository.findActiveByTitleIgnoreCase(rawTitle);
        if (!exactMatches.isEmpty()) {
            return exactMatches.get(0);
        }

        String normalized = rawTitle.strip().toLowerCase().replace(" ", "");
        List<Song> likeMatches = songRepository.findActiveByTitleContaining("%" + normalized + "%");
        if (!likeMatches.isEmpty()) {
            return likeMatches.get(0);
        }

        return songRepository.save(new Song(rawTitle, null, null, null, null));
    }

    @Transactional
    public String generateShareToken(Long setlistId) {
        Setlist setlist = getActive(setlistId);
        if (setlist.getShareToken() != null) {
            return setlist.getShareToken();
        }
        return setlist.generateShareToken();
    }

    @Transactional
    public void revokeShareToken(Long setlistId) {
        Setlist setlist = getActive(setlistId);
        setlist.revokeShareToken();
    }

    @Transactional(readOnly = true)
    public SharedSetlistResponse getByShareToken(String token) {
        Setlist setlist = setlistRepository.findByShareToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("공유 링크를 찾을 수 없습니다."));
        return SharedSetlistResponse.from(setlist);
    }

    private Setlist getActive(Long setlistId) {
        return setlistRepository.findActiveById(setlistId)
                .orElseThrow(() -> new ResourceNotFoundException("Setlist not found: " + setlistId));
    }
}
