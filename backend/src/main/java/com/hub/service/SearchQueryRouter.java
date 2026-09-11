package com.hub.service;

import com.hub.util.SearchText;
import com.hub.util.UnicodeText;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight, deterministic query router for Korean office search.
 * It extracts only high-confidence conditions and leaves ambiguous words to hybrid retrieval.
 */
@Service
public class SearchQueryRouter {
    private static final ZoneId SEARCH_ZONE = ZoneId.of("Asia/Seoul");
    private static final Pattern FILE = Pattern.compile("(?iu)([^\\s\\\"']+\\.(?:pdf|docx?|xlsx?|pptx?|csv|txt|md|hwp|hwpx|json|xml|html?))");
    private static final Pattern QUOTED_FILE = Pattern.compile("(?iu)[\"\']([^\"\']+\\.(?:pdf|docx?|xlsx?|pptx?|csv|txt|md|hwp|hwpx|json|xml|html?))[\"\']");
    private static final Pattern AUTHOR_PREFIX = Pattern.compile("(?iu)(?:작성자|등록자|올린\\s*사람)\\s*[:：]?\\s*([\\p{L}\\p{N}._-]{2,30})");
    private static final Pattern AUTHOR_VERB = Pattern.compile("(?iu)([\\p{L}\\p{N}._-]{2,30})(?:이|가|께서)?\\s*(?:작성한|작성했던|작성|등록한|등록했던|등록|올린)\\s*(?:자료|문서|파일|회의록)?");
    private static final Pattern QUESTION = Pattern.compile("(?iu)(왜|어떻게|무엇|뭐|언제|누가|어디서|어떤|알려줘|설명해|정리해|결정|이유)");
    private static final Pattern TEMPLATE = Pattern.compile("(?iu)(같은\\s*양식|비슷한\\s*양식|원본\\s*양식|기준\\s*양식|템플릿|서식)");
    private static final Pattern ISO_DATE = Pattern.compile("(?<!\\d)(\\d{4})[-./](\\d{1,2})[-./](\\d{1,2})(?!\\d)");
    private static final Pattern KOREAN_DATE = Pattern.compile("(?<!\\d)(\\d{1,2})월\\s*(\\d{1,2})일");

    public SearchQueryPlan route(String rawQuery) {
        String original = UnicodeText.nfc(rawQuery == null ? "" : rawQuery).trim();
        if (original.isBlank()) throw new IllegalArgumentException("자료 검색어가 필요합니다.");

        String exactFilename = exactFilename(original);
        String author = extractAuthor(original);
        DateRange range = dateRange(original);
        Set<String> sourceTypes = sourceTypes(original);
        boolean templateIntent = TEMPLATE.matcher(original).find();

        String searchText = cleanup(original, exactFilename, author, range.matchedPhrase(), sourceTypes);
        SearchQueryPlan.Intent intent;
        if (!exactFilename.isBlank()) intent = SearchQueryPlan.Intent.EXACT_FILE;
        else if (templateIntent) intent = SearchQueryPlan.Intent.TEMPLATE;
        else if (!author.isBlank() || range.from() != null || range.to() != null || !sourceTypes.isEmpty()) intent = SearchQueryPlan.Intent.FILTERED;
        else if (QUESTION.matcher(original).find()) intent = SearchQueryPlan.Intent.QUESTION;
        else intent = SearchQueryPlan.Intent.GENERAL;

        Weights weights = weights(intent, templateIntent);
        return new SearchQueryPlan(original, searchText, intent, exactFilename, author, range.from(), range.to(),
                sourceTypes, weights.rule(), weights.template(), weights.semantic(), weights.lexical(), weights.metadata());
    }

    private static Weights weights(SearchQueryPlan.Intent intent, boolean templateIntent) {
        return switch (intent) {
            case EXACT_FILE -> new Weights(5.0, templateIntent ? 3.5 : 0.8, 0.45, 2.8, 1.8);
            case TEMPLATE -> new Weights(5.5, 4.5, 0.85, 1.2, 1.2);
            case FILTERED -> new Weights(4.8, templateIntent ? 3.5 : 1.2, 1.0, 1.25, 3.0);
            case QUESTION -> new Weights(4.2, templateIntent ? 3.0 : 1.0, 1.7, 1.0, 1.0);
            case GENERAL -> new Weights(5.0, templateIntent ? 3.5 : 1.4, 1.15, 1.25, 1.0);
        };
    }

    private static String extractAuthor(String query) {
        Matcher prefixed = AUTHOR_PREFIX.matcher(query);
        if (prefixed.find()) return trimParticles(prefixed.group(1));
        Matcher verb = AUTHOR_VERB.matcher(query);
        if (verb.find()) return trimParticles(verb.group(1));
        return "";
    }

    private static String trimParticles(String value) {
        String v = value == null ? "" : value.trim();
        if (v.length() >= 3 && (v.endsWith("이") || v.endsWith("가"))) return v.substring(0, v.length() - 1);
        return v;
    }

    private static DateRange dateRange(String query) {
        LocalDate today = LocalDate.now(SEARCH_ZONE);
        if (contains(query, "오늘")) return day(today, "오늘");
        if (contains(query, "어제")) return day(today.minusDays(1), "어제");
        if (contains(query, "지난주") || contains(query, "지난 주")) {
            LocalDate thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            return range(thisMonday.minusWeeks(1), thisMonday, contains(query, "지난주") ? "지난주" : "지난 주");
        }
        if (contains(query, "이번주") || contains(query, "이번 주")) {
            LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            return range(monday, monday.plusWeeks(1), contains(query, "이번주") ? "이번주" : "이번 주");
        }
        if (contains(query, "지난달") || contains(query, "지난 달")) {
            LocalDate first = today.withDayOfMonth(1);
            return range(first.minusMonths(1), first, contains(query, "지난달") ? "지난달" : "지난 달");
        }
        if (contains(query, "이번달") || contains(query, "이번 달")) {
            LocalDate first = today.withDayOfMonth(1);
            return range(first, first.plusMonths(1), contains(query, "이번달") ? "이번달" : "이번 달");
        }
        Matcher iso = ISO_DATE.matcher(query);
        if (iso.find()) {
            try {
                LocalDate day = LocalDate.of(Integer.parseInt(iso.group(1)), Integer.parseInt(iso.group(2)), Integer.parseInt(iso.group(3)));
                return day(day, iso.group(0));
            } catch (RuntimeException ignored) { }
        }
        Matcher korean = KOREAN_DATE.matcher(query);
        if (korean.find()) {
            try {
                LocalDate day = LocalDate.of(today.getYear(), Integer.parseInt(korean.group(1)), Integer.parseInt(korean.group(2)));
                return day(day, korean.group(0));
            } catch (RuntimeException ignored) { }
        }
        return new DateRange(null, null, "");
    }

    private static DateRange day(LocalDate day, String phrase) {
        return range(day, day.plusDays(1), phrase);
    }

    private static DateRange range(LocalDate from, LocalDate to, String phrase) {
        ZoneOffset offset = SEARCH_ZONE.getRules().getOffset(from.atStartOfDay());
        OffsetDateTime start = from.atStartOfDay().atOffset(offset);
        ZoneOffset endOffset = SEARCH_ZONE.getRules().getOffset(to.atStartOfDay());
        OffsetDateTime end = to.atStartOfDay().atOffset(endOffset);
        return new DateRange(start, end, phrase);
    }

    private static Set<String> sourceTypes(String query) {
        String lower = SearchText.lower(query);
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (containsAny(lower, "회의 녹음", "녹음 기록", "녹음한 회의", "stt 기록")) out.add("MEETING_TRANSCRIPT");
        if (containsAny(lower, "내 pc", "로컬 파일", "내 컴퓨터")) out.add("LOCAL_PC");
        if (containsAny(lower, "github", "깃허브", "pull request", "풀리퀘스트", "깃 이슈", "github 이슈") || lower.matches(".*(?:^|\\s)#?pr[\\s#0-9].*")) out.add("GITHUB");
        if (containsAny(lower, "google drive", "구글 드라이브", "drive")) out.add("GOOGLE_DRIVE");
        if (containsAny(lower, "slack", "슬랙")) out.add("SLACK");
        if (containsAny(lower, "notion", "노션")) out.add("NOTION");
        return Set.copyOf(out);
    }

    private static String cleanup(String query, String filename, String author, String datePhrase, Set<String> sourceTypes) {
        String result = query;
        if (!filename.isBlank()) return filename;
        if (!datePhrase.isBlank()) result = result.replace(datePhrase, " ");
        if (!author.isBlank()) {
            result = AUTHOR_PREFIX.matcher(result).replaceAll(" ");
            result = AUTHOR_VERB.matcher(result).replaceAll(" ");
        }
        if (sourceTypes.contains("MEETING_TRANSCRIPT")) result = result.replaceAll("(?iu)회의록|회의\\s*녹음|녹음\\s*기록|회의\\s*기록", " ");
        if (sourceTypes.contains("LOCAL_PC")) result = result.replaceAll("(?iu)내\\s*PC|로컬\\s*파일|내\\s*컴퓨터", " ");
        if (sourceTypes.contains("GITHUB")) result = result.replaceAll("(?iu)GitHub|깃허브", " ");
        if (sourceTypes.contains("GOOGLE_DRIVE")) result = result.replaceAll("(?iu)Google\\s*Drive|구글\\s*드라이브", " ");
        if (sourceTypes.contains("SLACK")) result = result.replaceAll("(?iu)Slack|슬랙", " ");
        if (sourceTypes.contains("NOTION")) result = result.replaceAll("(?iu)Notion|노션", " ");
        result = result.replaceAll("(?iu)(찾아줘|찾아 줘|보여줘|보여 줘|알려줘|알려 줘|검색해줘|검색해 줘)", " ");
        result = result.replaceAll("(?iu)\\b(관련|자료|파일)\\b", " ");
        result = result.replaceAll("\\s+", " ").trim();
        return result.isBlank() ? query : result;
    }

    private static boolean contains(String query, String token) {
        return SearchText.lower(query).contains(SearchText.lower(token));
    }

    private static boolean containsAny(String query, String... values) {
        for (String value : values) if (query.contains(value.trim().toLowerCase(Locale.ROOT))) return true;
        return false;
    }

    private static String exactFilename(String value) {
        String quoted = find(QUOTED_FILE, value);
        return quoted.isBlank() ? find(FILE, value) : quoted;
    }

    private static String find(Pattern pattern, String value) {
        Matcher matcher = pattern.matcher(value);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    private record DateRange(OffsetDateTime from, OffsetDateTime to, String matchedPhrase) {}
    private record Weights(double rule, double template, double semantic, double lexical, double metadata) {}
}
