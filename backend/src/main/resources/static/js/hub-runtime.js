"use strict";
(function installHubRuntime() {
    const text = (value) => {
        if (value === null || value === undefined)
            return null;
        const normalized = String(value).trim();
        return normalized.length ? normalized : null;
    };
    const asRecord = (value) => value !== null && typeof value === "object" ? value : {};
    const asArray = (value) => Array.isArray(value) ? value : [];
    const first = (row, ...keys) => {
        for (const key of keys)
            if (row[key] !== undefined && row[key] !== null)
                return row[key];
        return null;
    };
    const normalizeTodo = (wireValue) => {
        const wire = asRecord(wireValue);
        return {
            id: typeof wire.id === "number" ? wire.id : null,
            title: text(wire.title) ?? "할 일 후보",
            description: text(wire.description),
            assigneeText: text(first(wire, "assigneeText", "assignee_text")),
            assigneeSuggestionText: text(first(wire, "assigneeSuggestionText", "assignee_suggestion_text")),
            dueDate: text(first(wire, "dueDate", "due_date")),
            dueDateSuggestion: text(first(wire, "dueDateSuggestion", "due_date_suggestion")),
            confidence: text(wire.confidence) ?? "LOW",
            evidenceQuote: text(first(wire, "evidenceQuote", "evidence_quote")) ?? ""
        };
    };
    const normalizeDecision = (wireValue) => {
        const wire = asRecord(wireValue);
        return {
            statement: text(wire.statement) ?? "",
            confidence: text(wire.confidence) ?? "LOW",
            evidenceQuote: text(first(wire, "evidenceQuote", "evidence_quote")) ?? ""
        };
    };
    const pad2 = (value) => String(value).padStart(2, "0");
    const localDate = (date = new Date()) => `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}`;
    const localMonth = (date = new Date()) => `${date.getFullYear()}-${pad2(date.getMonth() + 1)}`;
    const localDateTimeWithOffset = (date = new Date()) => {
        const offsetMinutes = -date.getTimezoneOffset();
        const sign = offsetMinutes >= 0 ? "+" : "-";
        const absolute = Math.abs(offsetMinutes);
        const offset = `${sign}${pad2(Math.floor(absolute / 60))}:${pad2(absolute % 60)}`;
        return `${localDate(date)}T${pad2(date.getHours())}:${pad2(date.getMinutes())}:${pad2(date.getSeconds())}${offset}`;
    };
    const normalizeAnalysis = (value) => {
        const wire = asRecord(value);
        return {
            summary: text(wire.summary) ?? "",
            todos: asArray(wire.todos).map(normalizeTodo),
            decisions: asArray(wire.decisions).map(normalizeDecision)
        };
    };
    const errorMessage = (error) => error instanceof Error ? error.message : typeof error === "string" ? error : "요청 처리 중 오류가 발생했습니다.";
    window.HubRuntime = { normalizeAnalysis, localDate, localMonth, localDateTimeWithOffset, errorMessage };
})();
