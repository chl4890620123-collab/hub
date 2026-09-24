package com.hub.service;

import com.hub.config.HubProperties;
import com.hub.repository.SearchRuleRepository;
import com.hub.util.UnicodeText;
import org.springframework.beans.factory.config.YamlMapFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Search aliases are operational configuration, not Java constants.
 * Project-managed rules override packaged/file defaults and can route a known alias to one exact filename.
 */
@Service
public class SearchRuleService {
    private final HubProperties props;
    private final SearchRuleRepository repository;
    private volatile List<SearchRule> cachedFileRules = List.of();
    private volatile long cachedModified = Long.MIN_VALUE;
    private volatile String cachedLocation = "";

    public SearchRuleService(HubProperties props, SearchRuleRepository repository) {
        this.props = props;
        this.repository = repository;
    }

    public Optional<RuleMatch> match(long projectId, String query) {
        String normalized = lower(query);
        if (normalized.isBlank()) return Optional.empty();
        RuleMatch best = null;
        int bestScore = Integer.MIN_VALUE;
        for (SearchRule rule : rules(projectId)) {
            for (String alias : rule.aliases()) {
                int score = aliasScore(normalized, alias) + rule.priority();
                if (score > bestScore) {
                    bestScore = score;
                    best = new RuleMatch(rule.id(), rule.name(), rule.patterns(), rule.mode(), alias,
                            rule.targetFile(), rule.priority(), rule.managed());
                }
            }
        }
        return bestScore > 0 ? Optional.of(best) : Optional.empty();
    }

    public List<SearchRule> rules(long projectId) {
        List<SearchRule> managed = repository.list(projectId, true).stream()
                .map(this::managedRule)
                .toList();
        Set<String> managedAliases = new LinkedHashSet<>();
        managed.forEach(rule -> rule.aliases().forEach(alias -> managedAliases.add(lower(alias))));

        List<SearchRule> merged = new ArrayList<>(managed);
        for (SearchRule fileRule : fileRules()) {
            List<String> aliases = fileRule.aliases().stream()
                    .filter(alias -> !managedAliases.contains(lower(alias)))
                    .toList();
            if (!aliases.isEmpty()) merged.add(new SearchRule(fileRule.id(), fileRule.projectId(), fileRule.name(),
                    aliases, fileRule.patterns(), fileRule.targetFile(), fileRule.mode(), fileRule.priority(),
                    fileRule.active(), false));
        }
        merged.sort(Comparator.comparingInt(SearchRule::priority).reversed().thenComparing(SearchRule::name));
        return List.copyOf(merged);
    }

    public List<SearchRule> managedRules(long projectId) {
        return repository.list(projectId, false).stream().map(this::managedRule).toList();
    }

    public SearchRule create(long projectId, RuleInput input, long actorId) {
        NormalizedRule n = normalize(input, null, projectId);
        long id = repository.create(projectId, n.name(), n.aliases(), n.patterns(), n.targetFile(), n.mode(), n.priority(), n.active(), actorId);
        return repository.find(projectId, id).map(this::managedRule).orElseThrow();
    }

    public SearchRule update(long projectId, long id, RuleInput input) {
        if (repository.find(projectId, id).isEmpty()) throw new IllegalArgumentException("검색 도움 설정을 찾을 수 없습니다.");
        NormalizedRule n = normalize(input, id, projectId);
        repository.update(projectId, id, n.name(), n.aliases(), n.patterns(), n.targetFile(), n.mode(), n.priority(), n.active());
        return repository.find(projectId, id).map(this::managedRule).orElseThrow();
    }

    public void delete(long projectId, long id) {
        repository.delete(projectId, id);
    }

    private NormalizedRule normalize(RuleInput input, Long editingId, long projectId) {
        if (input == null) throw new IllegalArgumentException("Search rule is required");
        String name = text(input.name());
        if (name.isBlank()) throw new IllegalArgumentException("검색 규칙 이름을 입력해 주세요.");
        if (name.length() > 200) throw new IllegalArgumentException("검색 도움 설정 이름은 200자 이하로 입력해 주세요.");
        List<String> patterns = normalizeList(input.patterns(), 20, 500);
        String targetFile = text(input.targetFile());
        if (targetFile.length() > 500) throw new IllegalArgumentException("Target filename is too long");
        if (patterns.isEmpty() && targetFile.isBlank()) throw new IllegalArgumentException("Reference file or filename condition is required");
        List<String> aliases = normalizeList(input.aliases(), 20, 100);
        if (aliases.isEmpty()) aliases = autoAliases(name, targetFile);
        if (aliases.isEmpty()) throw new IllegalArgumentException("Search words could not be inferred from the reference file");
        String mode = text(input.mode()).toUpperCase(Locale.ROOT);
        if (mode.isBlank()) mode = "SMART";
        if (!Set.of("SMART", "FULL").contains(mode)) throw new IllegalArgumentException("Search rule mode must be SMART or FULL");
        int priority = Math.max(0, Math.min(input.priority(), 1000));
        boolean active = input.active();

        Set<String> wanted = aliases.stream().map(SearchRuleService::lower).collect(java.util.stream.Collectors.toSet());
        for (SearchRuleRepository.Row row : repository.list(projectId, false)) {
            if (editingId != null && row.id() == editingId) continue;
            for (String alias : row.aliases()) {
                if (wanted.contains(lower(alias))) throw new IllegalArgumentException("Search alias is already used: " + alias);
            }
        }
        return new NormalizedRule(name, aliases, patterns, targetFile, mode, priority, active);
    }

    private List<SearchRule> fileRules() {
        String configured = props.searchRulesFile();
        if (configured == null || configured.isBlank()) return List.of();
        try {
            Resource resource = resource(configured.trim());
            if (!resource.exists()) return List.of();
            long modified = modified(resource, configured);
            if (configured.equals(cachedLocation) && modified == cachedModified) return cachedFileRules;
            List<SearchRule> loaded = load(resource);
            cachedFileRules = List.copyOf(loaded);
            cachedModified = modified;
            cachedLocation = configured;
            return cachedFileRules;
        } catch (Exception ignored) {
            return cachedFileRules;
        }
    }

    private SearchRule managedRule(SearchRuleRepository.Row row) {
        return new SearchRule(row.id(), row.projectId(), row.name(), row.aliases(), row.patterns(),
                text(row.targetFile()), row.mode(), row.priority(), row.active(), true);
    }

    private static int aliasScore(String normalizedQuery, String alias) {
        String token = lower(alias);
        if (token.isBlank()) return Integer.MIN_VALUE;
        if (normalizedQuery.equals(token)) return 10000;
        for (String queryToken : normalizedQuery.split("[^\\p{L}\\p{N}_.#/-]+")) {
            if (queryToken.equals(token)) return 8000;
        }
        if (normalizedQuery.contains(token)) return 6000;
        String compactQuery = compact(normalizedQuery);
        String compactToken = compact(token);
        if (compactToken.length() >= 2 && compactQuery.contains(compactToken)) return 5500;
        return Integer.MIN_VALUE;
    }

    private static List<String> autoAliases(String name, String targetFile) {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        addAutoAlias(values, name);
        addAutoAlias(values, name.replace("기준 자료", "").replace("기준", "").replace("원본", "").replace("양식", ""));
        String filename = targetFile == null ? "" : targetFile.trim();
        int dot = filename.lastIndexOf('.');
        String base = dot > 0 ? filename.substring(0, dot) : filename;
        addAutoAlias(values, base);
        for (String token : base.split("[^\\p{L}\\p{N}]+")) addAutoAlias(values, token);
        return values.values().stream().limit(20).toList();
    }

    private static void addAutoAlias(Map<String, String> values, String raw) {
        String value = text(raw).replaceAll("\\s+", " ").trim();
        if (value.length() < 2 || value.length() > 100 || value.matches("\\d+")) return;
        String key = lower(value);
        if (Set.of("final", "최종", "문서", "파일", "form", "template").contains(key)) return;
        values.putIfAbsent(key, value);
    }

    private static String compact(String value) {
        return lower(value).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private static Resource resource(String configured) {
        if (configured.startsWith("classpath:")) return new ClassPathResource(configured.substring("classpath:".length()));
        return new FileSystemResource(Path.of(configured).toAbsolutePath().normalize());
    }

    private static long modified(Resource resource, String configured) {
        if (configured.startsWith("classpath:")) return 0L;
        try { return Files.getLastModifiedTime(resource.getFile().toPath()).toMillis(); }
        catch (Exception ignored) { return 0L; }
    }

    @SuppressWarnings("unchecked")
    private static List<SearchRule> load(Resource resource) {
        YamlMapFactoryBean factory = new YamlMapFactoryBean();
        factory.setResources(resource);
        Map<String, Object> root = factory.getObject();
        if (root == null || !(root.get("rules") instanceof List<?> rawRules)) return List.of();
        List<SearchRule> out = new ArrayList<>();
        long syntheticId = -1;
        for (Object raw : rawRules) {
            if (!(raw instanceof Map<?, ?> map)) continue;
            String name = text(map.get("name"));
            List<String> aliases = strings(map.get("aliases"));
            List<String> patterns = strings(map.get("patterns"));
            String targetFile = text(map.get("targetFile"));
            String mode = text(map.get("mode"));
            int priority = integer(map.get("priority"), 10);
            if (patterns.isEmpty() && targetFile.isBlank()) continue;
            if (aliases.isEmpty()) aliases = autoAliases(name, targetFile);
            if (aliases.isEmpty()) continue;
            out.add(new SearchRule(syntheticId--, null, name.isBlank() ? aliases.get(0) : name,
                    aliases, patterns, targetFile, mode.isBlank() ? "SMART" : mode.toUpperCase(Locale.ROOT),
                    priority, true, false));
        }
        return out;
    }

    private static List<String> normalizeList(List<String> input, int maxItems, int maxLength) {
        if (input == null) return List.of();
        LinkedHashMap<String, String> unique = new LinkedHashMap<>();
        for (String raw : input) {
            String value = text(raw);
            if (value.isBlank()) continue;
            if (value.length() > maxLength) throw new IllegalArgumentException("Search rule value is too long");
            unique.putIfAbsent(lower(value), value);
            if (unique.size() > maxItems) throw new IllegalArgumentException("Too many search rule values");
        }
        return List.copyOf(unique.values());
    }

    private static List<String> strings(Object value) {
        if (value instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object item : list) { String t = text(item); if (!t.isBlank()) out.add(t); }
            return List.copyOf(out);
        }
        String single = text(value);
        return single.isBlank() ? List.of() : Collections.singletonList(single);
    }

    private static int integer(Object value, int fallback) {
        if (value instanceof Number n) return n.intValue();
        try { return Integer.parseInt(text(value)); } catch (Exception ignored) { return fallback; }
    }

    private static String text(Object value) { return UnicodeText.nfc(value == null ? "" : String.valueOf(value)).trim(); }
    private static String lower(String value) { return UnicodeText.nfc(value == null ? "" : value).toLowerCase(Locale.ROOT).trim(); }

    public record SearchRule(Long id, Long projectId, String name, List<String> aliases, List<String> patterns,
                             String targetFile, String mode, int priority, boolean active, boolean managed) {}
    public record RuleMatch(Long id, String name, List<String> patterns, String mode, String matchedAlias,
                            String targetFile, int priority, boolean managed) {}
    public record RuleInput(String name, List<String> aliases, List<String> patterns, String targetFile,
                            String mode, int priority, boolean active) {}
    private record NormalizedRule(String name, List<String> aliases, List<String> patterns, String targetFile,
                                  String mode, int priority, boolean active) {}
}
