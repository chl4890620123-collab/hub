package com.hub.service;

import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

@Component
public class ParagraphChunker {
    private static final int MAX_CHARS = 1500;
    private static final int OVERLAP = 100;

    // Keeps paragraph boundaries first, then applies bounded overlap only to oversized paragraphs.
    public List<String> chunk(String text) {
        List<String> out = new ArrayList<>();
        for (String block : text.replace("\r\n", "\n").split("\n\\s*\n")) {
            String value = block.trim();
            if (value.isBlank()) continue;
            if (value.length() <= MAX_CHARS) { out.add(value); continue; }
            int start = 0;
            while (start < value.length()) {
                int end = Math.min(value.length(), start + MAX_CHARS);
                out.add(value.substring(start, end));
                if (end == value.length()) break;
                start = Math.max(0, end - OVERLAP);
            }
        }
        return out;
    }
}
