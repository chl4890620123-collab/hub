// Wire types for the Hub REST API, kept close to backend/src/main/java/com/hub/{model,controller}
// so this file can be diffed against the backend when either side changes. Endpoints that return a
// raw `Map<String,Object>` (JdbcTemplate row projections) come back snake_case; endpoints returning a
// Java record come back camelCase. Both are documented per-type below - this is a real backend
// inconsistency (see docs/API_CONTRACT.md), not a mistake in this file.

export type GlobalRole = 'ADMIN' | 'MEMBER';
export type AccountStatus = 'ACTIVE' | 'SUSPENDED' | 'WITHDRAWN';
export type ApprovalStatus = 'PENDING' | 'APPROVED' | 'REJECTED';
export type TaskStatus = 'TODO' | 'IN_PROGRESS' | 'DONE' | 'BLOCKED';
export type ReviewStatus = 'AI_GENERATED' | 'REVIEWING' | 'CONFIRMED' | 'REJECTED';
export type AssignmentStatus = 'ACTIVE' | 'REASSIGNMENT_REQUIRED';
export type ConfidenceLevel = 'LOW' | 'MEDIUM' | 'HIGH';

export interface OrganizationDepartment { id: number; name: string; active: boolean; }
export interface OrganizationTeam { id: number; departmentId: number; name: string; active: boolean; }

export interface User {
  id: number;
  loginId: string;
  email: string;
  displayName: string;
  companyName: string | null;
  departmentName: string | null;
  teamName: string | null;
  jobTitle: string | null;
  globalRole: GlobalRole;
  accountStatus: AccountStatus;
  mustChangePassword: boolean;
  approvalStatus: ApprovalStatus;
}

export interface Project {
  id: number;
  name: string;
  description: string | null;
  departmentName: string | null;
  teamName: string | null;
  createdBy: number;
  projectRole: GlobalRole;
  canConfirm: boolean;
}

export interface ProjectMember {
  id: number;
  displayName: string;
  loginId: string;
  jobTitle: string | null;
  projectRole?: string;
  canConfirm?: boolean;
}

export interface ProjectOption {
  id: number;
  name: string;
  departmentName: string | null;
  teamName: string | null;
}

export interface TodoItem {
  id: number;
  projectId: number;
  title: string;
  description: string | null;
  assigneeId: number | null;
  assigneeText: string | null;
  assigneeSuggestionId: number | null;
  assigneeSuggestionText: string | null;
  dueDate: string | null;
  dueDateSuggestion: string | null;
  confidence: ConfidenceLevel;
  reviewStatus: ReviewStatus;
  taskStatus: TaskStatus;
  assignmentStatus: AssignmentStatus;
  possibleDuplicateOfId: number | null;
  duplicateReason: string | null;
  createdAt: string;
  updatedAt: string;
  /** Set once confirming this todo (with a due date) created a matching event on the assignee's own
   * Google Calendar - null if never synced (no due date, assignee hasn't linked Google, or scope). */
  googleCalendarEventId: string | null;
  /** True once the assignee has asked a decision-maker to review the work - taskStatus is untouched
   * until approved (becomes DONE) or rejected (stays as-is, statusNote carries the reason). */
  pendingApproval: boolean;
  /** Context for the todo's current non-normal state: the assignee's help-request note while
   * taskStatus is BLOCKED, or the decision-maker's reason after the last completion rejection. */
  statusNote: string | null;
  deletedAt: string | null;
  deletedBy: number | null;
}

/** decision_candidate row - GET pending/review endpoints. snake_case DB projection. */
export interface DecisionRow {
  id: number;
  statement: string;
  confidence: ConfidenceLevel;
  review_status: ReviewStatus;
  created_at: string;
}

/** change_item row (joined with its analysis) - GET list/review endpoints. snake_case DB projection. */
export interface ChangeItemRow {
  id: number;
  category: string;
  before_text: string;
  after_text: string;
  reason: string;
  review_status: ReviewStatus;
  created_at: string;
}

/** POST /api/projects/{id}/changes (version compare) result - a plain Java record, camelCase. */
export interface ChangeCompareItem {
  category: string;
  before: string;
  after: string;
  reason: string;
}

export interface ChangeCompareResponse {
  changes: ChangeCompareItem[];
}

export interface MaterialHit {
  evidenceId: number;
  sourceType: 'HUB' | 'GITHUB' | 'GOOGLE_DRIVE' | 'SLACK' | 'NOTION' | string;
  sourceLabel: string;
  itemType: string;
  title: string;
  location: string | null;
  snippet: string;
  author: string | null;
  sourceUrl: string | null;
  sourceCreatedAt: string | null;
  recommendationRank: number;
  matchType: string;
  recommendationReason: string;
}

export interface MaterialAskResponse {
  answer: string;
  sources: MaterialHit[];
}

export interface WorkContextBundle {
  query: string;
  summary: string;
  sources: MaterialHit[];
  todos: TodoItem[];
  decisions: DecisionRow[];
  changes: ChangeItemRow[];
  timeline: TimelineEvent[];
}

export interface TimelineEvent {
  id: number;
  projectId: number;
  eventType: string;
  title: string;
  description: string | null;
  happenedAt: string;
  sourceType: string | null;
  sourceId: number | null;
}

export type JobStatus = 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED';

export interface ProcessingJob {
  id: number;
  projectId: number;
  jobType: string;
  targetType: string;
  targetId: number | null;
  status: JobStatus;
  progress: number;
  errorCode: string | null;
  errorMessage: string | null;
  resultJson: string | null;
  createdAt: string;
  updatedAt: string;
}

/** GET /api/projects/{id}/documents - DocumentRepository.listDocuments() row. */
export interface DocumentRow {
  id: number;
  original_name: string;
  source_type: string;
  source_identifier: string | null;
  archived: boolean;
  source_deleted: boolean;
  created_at: string;
  latest_version: number;
  has_original: boolean;
  content_purged?: boolean;
  [key: string]: unknown;
}

/** GET /api/documents/{id}/versions - DocumentRepository.listVersions() row. */
export interface DocumentVersionRow {
  id: number;
  version_no: number;
  sha256: string;
  parse_status: string;
  summary: string | null;
  created_at: string;
  [key: string]: unknown;
}

export interface EvidenceView {
  id: number;
  versionId: number | null;
  chunkId: number | null;
  quote: string;
  contentHash: string | null;
  documentName: string | null;
  paragraphRef: string | null;
  pageNo: number | null;
  meetingTitle: string | null;
  startMs: number | null;
  endMs: number | null;
  speaker: string | null;
}

export interface ChangeEvidenceView {
  side: 'BEFORE' | 'AFTER';
  id: number;
  versionId: number | null;
  chunkId: number | null;
  quote: string;
  contentHash: string | null;
  documentName: string | null;
  paragraphRef: string | null;
  pageNo: number | null;
}

export interface FileTransferRecipient {
  id: number;
  displayName: string;
  loginId: string;
  admin: boolean;
}

export interface AttachmentView {
  id: number;
  todoId: number | null;
  senderId: number;
  recipientId: number | null;
  fileName: string;
  contentType: string | null;
  sizeBytes: number;
  note: string | null;
  read: boolean;
  createdAt: string;
}

export interface SignupApplication {
  id: number;
  loginId: string;
  email: string;
  displayName: string;
  companyName: string | null;
  departmentName: string | null;
  teamName: string | null;
  departmentId: number | null;
  teamId: number | null;
  jobTitle: string | null;
  signupNote: string | null;
  requestedProjectId: number | null;
  requestedProjectName: string | null;
  requestedRole: GlobalRole;
  approvalStatus: ApprovalStatus;
  rejectionReason: string | null;
  createdAt: string;
}

export interface ReassignmentRequest {
  id: number;
  todo_id?: number;
  project_id?: number;
  former_assignee_id?: number | null;
  former_assignee_name?: string | null;
  title?: string;
  task_status?: TaskStatus;
  due_date?: string | null;
  reason?: string;
  created_at?: string;
  [key: string]: unknown;
}

export interface SensitiveTerm {
  id: number;
  term: string;
  createdAt?: string;
  [key: string]: unknown;
}

export interface SearchRule {
  id: number | null;
  projectId: number | null;
  name: string;
  aliases: string[];
  patterns: string[];
  targetFile?: string | null;
  mode: 'SMART' | 'FULL';
  priority: number;
  active: boolean;
  managed: boolean;
}

export interface RuleInput {
  name: string;
  aliases: string[];
  patterns: string[];
  targetFile?: string | null;
  mode: 'SMART' | 'FULL';
  priority: number;
  active: boolean;
}

export type ConnectorType = 'GITHUB' | 'GOOGLE_DRIVE' | 'SLACK' | 'NOTION';

export interface ConnectorSyncState {
  connectorType: string;
  externalScope: string;
  lastSyncedAt: string | null;
  lastStatus: string | null;
  lastError: string | null;
  lastImportedCount: number;
}

/** One importable repo/folder/channel/page - id is the exact "scope" value sent back to /import. */
export interface ConnectorTargetOption {
  id: string;
  name: string;
  description: string;
  url: string;
}

export interface ConnectorTargetsResponse {
  connected: boolean;
  linkedByUser: boolean;
  account: string | null;
  targets: ConnectorTargetOption[];
}

export interface AuditLogRow {
  id: number;
  user_id: number | null;
  display_name: string | null;
  project_id: number | null;
  project_name: string | null;
  action: string;
  target_type: string | null;
  target_id: number | null;
  detail_json: string | null;
  created_at: string;
}

export interface RevisionRow {
  id: number;
  entity_type: string;
  entity_id: number;
  action: string;
  before_json: string | null;
  after_json: string | null;
  created_at: string;
  actor_name: string;
}

// 자료표 (Spreadsheet*.java records, so camelCase like the rest of this section's record-backed types).
export interface SpreadsheetColumn {
  key: string;
  label: string;
}

export interface SpreadsheetFileRow {
  id: number;
  projectId: number;
  name: string;
  ownerId: number;
  columns: SpreadsheetColumn[];
  passwordProtected: boolean;
  passwordHint: string | null;
  rowCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface SpreadsheetDataRow {
  id: number;
  fileId: number;
  position: number;
  cells: Record<string, string>;
  updatedAt: string;
}

export interface SpreadsheetUnlocked {
  file: SpreadsheetFileRow;
  rows: SpreadsheetDataRow[];
}
