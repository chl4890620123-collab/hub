// Keep wire names here so snake_case/camelCase normalization is never duplicated across screens.
namespace HubContracts {
  export interface TodoWire {
    title?: unknown;
    description?: unknown;
    assignee_text?: unknown;
    assigneeText?: unknown;
    assignee_suggestion_text?: unknown;
    assigneeSuggestionText?: unknown;
    due_date?: unknown;
    dueDate?: unknown;
    due_date_suggestion?: unknown;
    dueDateSuggestion?: unknown;
    confidence?: unknown;
    evidence_quote?: unknown;
    evidenceQuote?: unknown;
  }

  export interface DecisionWire {
    statement?: unknown;
    confidence?: unknown;
    evidence_quote?: unknown;
    evidenceQuote?: unknown;
  }

  export interface AnalyzeWire {
    summary?: unknown;
    todos?: unknown;
    decisions?: unknown;
  }

  export interface TodoView {
    title: string;
    description: string | null;
    assigneeText: string | null;
    assigneeSuggestionText: string | null;
    dueDate: string | null;
    dueDateSuggestion: string | null;
    confidence: string;
    evidenceQuote: string;
  }

  export interface DecisionView {
    statement: string;
    confidence: string;
    evidenceQuote: string;
  }

  export interface AnalyzeView {
    summary: string;
    todos: TodoView[];
    decisions: DecisionView[];
  }

  export interface Runtime {
    normalizeAnalysis(value: unknown): AnalyzeView;
    localDate(date?: Date): string;
    localMonth(date?: Date): string;
    localDateTimeWithOffset(date?: Date): string;
    errorMessage(error: unknown): string;
  }
}
