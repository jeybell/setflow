package com.jeybell.sheetmusic.setlist;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 콘티를 준비할 때 흔히 쓰는 텍스트 형식(제목 줄 + 곡 목록)을 파싱한다. 예:
 * <pre>
 * &lt;2026.9.2.(수)저녁 만나예배&gt;
 * ・(Intro)목마른 예배자 F
 *   +(후렴만)온 맘 다해 F
 * </pre>
 * 첫 줄의 {@code <...>} 는 날짜(첫 번째로 발견되는 YYYY.M.D 패턴)와 콘티 제목(괄호 안 전체)으로,
 * 이후 각 줄은 한 곡씩(불릿·"+" 들여쓰기 구분 없이 순서대로)으로 해석한다. 줄 앞의 {@code (...)}
 * 표시는 메모로, 줄 끝의 코드/키 토큰(F, G, Am 등)은 연주 키로 분리한다. 키가 없는 줄은 제목만 남는다.
 */
public final class SetlistTextParser {

    private static final Pattern HEADER = Pattern.compile("^<(.+)>$");
    private static final Pattern DATE_IN_HEADER = Pattern.compile("(\\d{4})[.\\-/](\\d{1,2})[.\\-/](\\d{1,2})");
    private static final Pattern BULLET_PREFIX = Pattern.compile("^[\\-•*·・+]\\s*");
    private static final Pattern LEADING_PARENS = Pattern.compile("^\\(([^)]*)\\)\\s*");
    private static final Pattern TRAILING_KEY = Pattern.compile("^(.*?)\\s+([A-Ga-g](?:#|b)?m?)$");

    private SetlistTextParser() {
    }

    public record ParsedItem(String rawTitle, String performanceKey, String memo) {
    }

    public record ParsedSetlist(LocalDate serviceDate, String title, List<ParsedItem> items) {
    }

    public static ParsedSetlist parse(String text) {
        LocalDate date = null;
        String title = null;
        List<ParsedItem> items = new ArrayList<>();

        for (String rawLine : text.split("\\R")) {
            String line = rawLine.strip();
            if (line.isEmpty()) {
                continue;
            }

            Matcher headerMatcher = HEADER.matcher(line);
            if (headerMatcher.matches()) {
                title = headerMatcher.group(1).strip();
                Matcher dateMatcher = DATE_IN_HEADER.matcher(title);
                if (dateMatcher.find()) {
                    date = LocalDate.of(
                            Integer.parseInt(dateMatcher.group(1)),
                            Integer.parseInt(dateMatcher.group(2)),
                            Integer.parseInt(dateMatcher.group(3))
                    );
                }
                continue;
            }

            String body = BULLET_PREFIX.matcher(line).replaceFirst("");
            StringBuilder memo = new StringBuilder();
            Matcher parenMatcher = LEADING_PARENS.matcher(body);
            while (parenMatcher.find()) {
                if (!memo.isEmpty()) {
                    memo.append(' ');
                }
                memo.append('(').append(parenMatcher.group(1)).append(')');
                body = body.substring(parenMatcher.end());
                parenMatcher = LEADING_PARENS.matcher(body);
            }
            body = body.strip();
            if (body.isEmpty()) {
                continue;
            }

            String rawTitle = body;
            String key = null;
            Matcher keyMatcher = TRAILING_KEY.matcher(body);
            if (keyMatcher.matches()) {
                rawTitle = keyMatcher.group(1).strip();
                key = keyMatcher.group(2);
            }

            items.add(new ParsedItem(rawTitle, key, memo.isEmpty() ? null : memo.toString()));
        }

        return new ParsedSetlist(date, title, items);
    }
}
