package com.hub.service;

import com.hub.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * Creates a repeatable, real-database filming workspace when HUB_DEMO_MODE=true.
 *
 * Passwords are intentionally never defined in source. The deployment host supplies them through
 * HUB_DEMO_ADMIN_PASSWORD / HUB_DEMO_MEMBER_PASSWORD. Re-running the service restores the known
 * demo rows to the beginning of the filming story without touching ordinary users/projects.
 */
@Component
@Order(100)
public class DemoSeedService implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoSeedService.class);
    private static final String PROJECT_NAME = "Hub 협업 촬영 데모";
    private static final String COMPANY = "Hub Demo";
    private static final String DEPARTMENT = "제품개발부";
    private static final String TEAM = "플랫폼팀";

    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final ProjectRepository projects;
    private final OrganizationRepository organization;
    private final DocumentRepository documents;
    private final MeetingRepository meetings;
    private final TodoRepository todos;
    private final ReassignmentRepository reassignments;
    private final DecisionRepository decisions;
    private final ChangeRepository changes;
    private final EvidenceRepository evidence;
    private final ProcessingJobRepository jobs;
    private final TimelineRepository timeline;
    private final SearchRuleRepository searchRules;
    private final SpreadsheetRepository sheets;
    private final ConnectorRepository connectors;
    private final SensitiveTermRepository sensitiveTerms;
    private final AuditRepository audit;
    private final PasswordEncoder encoder;
    private final PasswordPolicy passwordPolicy;

    private final boolean enabled;
    private final String adminLoginId;
    private final String adminPassword;
    private final String memberLoginId;
    private final String memberPassword;

    public DemoSeedService(
            JdbcTemplate jdbc,
            UserRepository users,
            ProjectRepository projects,
            OrganizationRepository organization,
            DocumentRepository documents,
            MeetingRepository meetings,
            TodoRepository todos,
            ReassignmentRepository reassignments,
            DecisionRepository decisions,
            ChangeRepository changes,
            EvidenceRepository evidence,
            ProcessingJobRepository jobs,
            TimelineRepository timeline,
            SearchRuleRepository searchRules,
            SpreadsheetRepository sheets,
            ConnectorRepository connectors,
            SensitiveTermRepository sensitiveTerms,
            AuditRepository audit,
            PasswordEncoder encoder,
            PasswordPolicy passwordPolicy,
            @Value("${hub.demo-mode:false}") boolean enabled,
            @Value("${hub.demo-admin-login-id:video-admin}") String adminLoginId,
            @Value("${hub.demo-admin-password:}") String adminPassword,
            @Value("${hub.demo-member-login-id:video-member}") String memberLoginId,
            @Value("${hub.demo-member-password:}") String memberPassword
    ) {
        this.jdbc = jdbc;
        this.users = users;
        this.projects = projects;
        this.organization = organization;
        this.documents = documents;
        this.meetings = meetings;
        this.todos = todos;
        this.reassignments = reassignments;
        this.decisions = decisions;
        this.changes = changes;
        this.evidence = evidence;
        this.jobs = jobs;
        this.timeline = timeline;
        this.searchRules = searchRules;
        this.sheets = sheets;
        this.connectors = connectors;
        this.sensitiveTerms = sensitiveTerms;
        this.audit = audit;
        this.encoder = encoder;
        this.passwordPolicy = passwordPolicy;
        this.enabled = enabled;
        this.adminLoginId = cleanLogin(adminLoginId, "video-admin");
        this.adminPassword = adminPassword == null ? "" : adminPassword;
        this.memberLoginId = cleanLogin(memberLoginId, "video-member");
        this.memberPassword = memberPassword == null ? "" : memberPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled) return;
        if (adminPassword.isBlank() || memberPassword.isBlank()) {
            log.warn("HUB_DEMO_MODE is enabled but demo passwords are blank; real demo seed was skipped.");
            return;
        }
        try {
            passwordPolicy.validate(adminPassword);
            passwordPolicy.validate(memberPassword);
        } catch (IllegalArgumentException invalid) {
            log.warn("Demo passwords do not meet the password policy ({}); real demo seed was skipped.", invalid.getMessage());
            return;
        }

        long adminId = ensureAdmin();
        long projectId = ensureProject(adminId);
        long memberId = ensureMember(memberLoginId, memberPassword, "박준호", "프론트엔드 엔지니어", projectId, adminId, true);
        long formerId = ensureMember(derivedLogin(memberLoginId, "former"), memberPassword, "김민수", "전 담당자", projectId, adminId, false);
        long applicantId = ensurePendingApplicant(projectId);

        assignOrganization(adminId, memberId);
        ensureProjectMembership(projectId, memberId, adminId);
        resetApplicant(applicantId, projectId);

        SeedDocument plan = ensurePlanDocument(projectId, adminId);
        SeedDocument interview = ensureSingleDocument(
                projectId, adminId, "demo:customer-interviews", "촬영용 고객 인터뷰 요약",
                "신규 사용자는 담당 업무와 관련 자료를 한 화면에서 확인하고 싶어 합니다. " +
                        "검색 결과에는 원문 위치와 출처가 보여야 신뢰할 수 있다는 의견이 반복되었습니다. " +
                        "관리자와 담당자가 완료 요청과 승인 상태를 주고받는 흐름도 필요합니다.",
                "고객 인터뷰에서는 업무·자료·승인 상태를 한 화면에서 연결해서 보는 요구가 가장 많이 확인되었습니다."
        );
        SeedMeeting meeting = ensureMeeting(projectId, memberId);
        SeedDocument transcript = ensureMeetingTranscript(projectId, memberId, meeting);

        long candidateTodoId = ensureCandidateTodo(projectId, meeting, transcript.versionId(), memberId);
        long blockedTodoId = ensureConfirmedTodo(projectId, memberId, adminId,
                "[촬영] 검색 결과 출처 배지 추가", "검색 결과 카드에 원문 출처와 위치를 표시합니다.", LocalDate.now().plusDays(3));
        resetBlockedTodo(blockedTodoId, "디자인 검토가 필요합니다. 관리자와 확인 후 다시 진행합니다.");

        long approvalTodoId = ensureConfirmedTodo(projectId, memberId, adminId,
                "[촬영] 배포 체크리스트 최종 검수", "담당자가 작업을 마치고 관리자에게 완료 승인을 요청한 업무입니다.", LocalDate.now().plusDays(1));
        resetPendingApproval(approvalTodoId);

        long reassignmentTodoId = ensureConfirmedTodo(projectId, formerId, adminId,
                "[촬영] 온보딩 인터뷰 3건 예약", "기존 담당자가 프로젝트에서 빠져 새 담당자를 정해야 합니다.", LocalDate.now().plusDays(4));
        resetReassignment(reassignmentTodoId, projectId, formerId, adminId);

        long decisionId = ensureDecision(projectId, plan.versionId(), meeting.id());
        long changeId = ensureChange(projectId, adminId, plan.firstVersionId(), plan.versionId());
        ensureEvidence(candidateTodoId, decisionId, changeId, plan, meeting);

        ensureSuccessfulJob(projectId, "DOCUMENT_ANALYSIS", "DOCUMENT_VERSION", plan.versionId(),
                "DEMO_DOCUMENT_ANALYSIS:" + plan.versionId(),
                "{\"summary\":\"베타 일정과 검색 근거 표시 기준을 정리했습니다.\",\"todos\":[],\"decisions\":[]}");
        ensureSuccessfulJob(projectId, "MEETING_STT_ANALYSIS", "MEETING", meeting.id(),
                "DEMO_MEETING_STT_ANALYSIS:" + meeting.id(),
                "{\"summary\":\"베타 일정과 검색 결과 출처 표시를 확정하고 박준호 담당 업무를 정리했습니다.\"," +
                        "\"todos\":[{\"id\":" + candidateTodoId + "}],\"decisions\":[{\"statement\":\"첫 베타는 초대 팀 대상으로 운영합니다.\"}]}");

        ensureSearchRule(projectId, adminId);
        ensureSpreadsheet(projectId, adminId);
        ensureExternalDemoItems(projectId);
        ensureSecurityTerms(adminId);
        ensureTimeline(projectId, candidateTodoId, approvalTodoId, plan.documentId());
        ensureAudit(adminId, memberId, projectId, candidateTodoId, approvalTodoId);

        log.warn("Real demo seed is ACTIVE for project '{}'. Demo credentials remain server-local and are not logged.", PROJECT_NAME);
    }

    private long ensureAdmin() {
        Optional<UserRepository.AuthUser> existing = users.findAuthByIdentifier(adminLoginId);
        long id = existing.map(UserRepository.AuthUser::id).orElseGet(() ->
                users.createBootstrapAdmin(adminLoginId, adminLoginId + "@hub-demo.local", encoder.encode(adminPassword),
                        "영상용 관리자", COMPANY, DEPARTMENT, TEAM, false));
        jdbc.update("""
                UPDATE app_user
                SET password_hash=?,display_name='영상용 관리자',company_name=?,department_name=?,team_name=?,
                    job_title='프로젝트 관리자',global_role='ADMIN',requested_role='ADMIN',
                    account_status='ACTIVE',approval_status='APPROVED',must_change_password=FALSE,
                    failed_login_count=0,locked_until=NULL,rejection_reason=NULL,rejected_at=NULL,
                    approved_at=COALESCE(approved_at,CURRENT_TIMESTAMP),auth_version=auth_version+1
                WHERE id=?
                """, encoder.encode(adminPassword), COMPANY, DEPARTMENT, TEAM, id);
        return id;
    }

    private long ensureMember(String loginId, String password, String name, String jobTitle,
                              long projectId, long adminId, boolean activeMember) {
        Optional<UserRepository.AuthUser> existing = users.findAuthByIdentifier(loginId);
        long id;
        if (existing.isPresent()) {
            id = existing.get().id();
        } else {
            id = users.createSignup(loginId, loginId + "@hub-demo.local", encoder.encode(password), name,
                    COMPANY, DEPARTMENT, TEAM, jobTitle, "촬영용 데모 계정", activeMember ? projectId : null, "MEMBER");
        }
        jdbc.update("""
                UPDATE app_user
                SET password_hash=?,display_name=?,company_name=?,department_name=?,team_name=?,job_title=?,
                    global_role='MEMBER',requested_role='MEMBER',account_status='ACTIVE',approval_status='APPROVED',
                    must_change_password=FALSE,failed_login_count=0,locked_until=NULL,rejection_reason=NULL,
                    rejected_at=NULL,approved_by=?,approved_at=COALESCE(approved_at,CURRENT_TIMESTAMP),
                    requested_project_id=?,auth_version=auth_version+1
                WHERE id=?
                """, encoder.encode(password), name, COMPANY, DEPARTMENT, TEAM, jobTitle, adminId,
                activeMember ? projectId : null, id);
        return id;
    }

    private long ensurePendingApplicant(long projectId) {
        String loginId = derivedLogin(memberLoginId, "applicant");
        Optional<UserRepository.AuthUser> existing = users.findAuthByIdentifier(loginId);
        if (existing.isPresent()) return existing.get().id();
        return users.createSignup(loginId, loginId + "@hub-demo.local", encoder.encode(memberPassword),
                "최민지", COMPANY, "고객경험부", "CS팀", "CS 매니저",
                "촬영에서 관리자의 가입 승인과 프로젝트 배정 과정을 보여주기 위한 신청입니다.",
                projectId, "MEMBER");
    }

    private void resetApplicant(long userId, long projectId) {
        jdbc.update("DELETE FROM project_member WHERE project_id=? AND user_id=?", projectId, userId);
        jdbc.update("""
                UPDATE app_user
                SET password_hash=?,display_name='최민지',company_name=?,department_name='고객경험부',team_name='CS팀',
                    job_title='CS 매니저',global_role='MEMBER',requested_role='MEMBER',
                    account_status='SUSPENDED',approval_status='PENDING',must_change_password=FALSE,
                    requested_project_id=?,signup_note='관리자 승인 후 프로젝트 협업에 참여하려고 합니다.',
                    approved_by=NULL,approved_at=NULL,rejected_at=NULL,rejection_reason=NULL,
                    failed_login_count=0,locked_until=NULL,auth_version=auth_version+1
                WHERE id=?
                """, encoder.encode(memberPassword), COMPANY, projectId, userId);
    }

    private long ensureProject(long adminId) {
        List<Long> ids = jdbc.query("SELECT id FROM project WHERE name=? ORDER BY id LIMIT 1",
                (rs, n) -> rs.getLong(1), PROJECT_NAME);
        if (!ids.isEmpty()) {
            projects.rename(ids.get(0), PROJECT_NAME,
                    "관리자와 일반 사용자가 AI·문서·회의·할 일을 함께 처리하는 실제 촬영용 프로젝트입니다.",
                    DEPARTMENT, TEAM);
            return ids.get(0);
        }
        return projects.create(PROJECT_NAME,
                "관리자와 일반 사용자가 AI·문서·회의·할 일을 함께 처리하는 실제 촬영용 프로젝트입니다.",
                DEPARTMENT, TEAM, adminId);
    }

    private void assignOrganization(long adminId, long memberId) {
        long departmentId = organization.departments(false).stream()
                .filter(d -> DEPARTMENT.equals(d.name())).map(OrganizationRepository.Department::id)
                .findFirst().orElseGet(() -> organization.createDepartment(DEPARTMENT));
        long teamId = organization.teamsForDepartment(departmentId, false).stream()
                .filter(t -> TEAM.equals(t.name())).map(OrganizationRepository.Team::id)
                .findFirst().orElseGet(() -> organization.createTeam(departmentId, TEAM));
        OrganizationRepository.Selection selection = organization.selection(departmentId, teamId).orElseThrow();
        organization.assignUser(adminId, selection);
        organization.assignUser(memberId, selection);
    }

    private void ensureProjectMembership(long projectId, long memberId, long adminId) {
        if (projects.addMember(projectId, memberId)) {
            projects.recordMemberJoin(projectId, memberId, adminId, "DEMO_SEED");
        }
    }

    private SeedDocument ensurePlanDocument(long projectId, long adminId) {
        String sourceId = "demo:product-plan";
        long documentId = documents.findDocumentId(projectId, "MANUAL_TEXT", sourceId)
                .orElseGet(() -> documents.createDocument(projectId, "MANUAL_TEXT", sourceId,
                        "촬영용 Atlas 베타 운영 계획", null, adminId));

        DocumentRepository.LatestVersion latest = documents.latestVersion(documentId).orElse(null);
        long firstVersionId;
        long latestVersionId;
        if (latest == null) {
            String v1 = "Atlas 베타는 9월 20일 전체 공개를 목표로 합니다. 검색 결과에는 제목만 표시합니다.";
            firstVersionId = documents.createVersion(documentId, sha256(v1), v1, "READY");
            documents.createChunk(firstVersionId, 0, "1. 초기 계획", v1);
            documents.updateSummary(firstVersionId, "초기 계획은 9월 20일 전체 공개와 단순 검색 결과 표시였습니다.");
            documents.markEmbeddingStatus(firstVersionId, "READY", null, false);

            String v2 = planText();
            latestVersionId = documents.createVersion(documentId, sha256(v2), v2, "READY");
            documents.createChunk(latestVersionId, 0, "1. 베타 범위", "첫 베타는 초대된 사내 팀을 대상으로 운영합니다.");
            documents.createChunk(latestVersionId, 1, "2. 일정", "베타 공개 일정은 9월 27일로 조정하며 사용성 테스트 결과를 반영합니다.");
            documents.createChunk(latestVersionId, 2, "3. 검색 원칙", "검색 결과에는 원문 출처, 위치, 최신 동기화 시간을 함께 표시합니다.");
            documents.updateSummary(latestVersionId, "초대 팀 대상 베타, 9월 27일 일정, 검색 결과의 원문 근거 표시를 핵심 운영 원칙으로 정리했습니다.");
            documents.markEmbeddingStatus(latestVersionId, "READY", null, false);
        } else if (latest.versionNo() == 1) {
            firstVersionId = latest.id();
            String v2 = planText();
            latestVersionId = documents.createVersion(documentId, sha256(v2), v2, "READY");
            documents.createChunk(latestVersionId, 0, "1. 베타 범위", "첫 베타는 초대된 사내 팀을 대상으로 운영합니다.");
            documents.createChunk(latestVersionId, 1, "2. 일정", "베타 공개 일정은 9월 27일로 조정하며 사용성 테스트 결과를 반영합니다.");
            documents.createChunk(latestVersionId, 2, "3. 검색 원칙", "검색 결과에는 원문 출처, 위치, 최신 동기화 시간을 함께 표시합니다.");
            documents.updateSummary(latestVersionId, "초대 팀 대상 베타, 9월 27일 일정, 검색 결과의 원문 근거 표시를 핵심 운영 원칙으로 정리했습니다.");
            documents.markEmbeddingStatus(latestVersionId, "READY", null, false);
        } else {
            firstVersionId = jdbc.queryForObject(
                    "SELECT id FROM document_version WHERE document_id=? ORDER BY version_no ASC LIMIT 1", Long.class, documentId);
            latestVersionId = latest.id();
            documents.updateSummary(latestVersionId, "초대 팀 대상 베타, 9월 27일 일정, 검색 결과의 원문 근거 표시를 핵심 운영 원칙으로 정리했습니다.");
            documents.markEmbeddingStatus(latestVersionId, "READY", null, false);
        }

        ensureArchivedDocument(projectId, adminId);
        return new SeedDocument(documentId, firstVersionId, latestVersionId);
    }

    private SeedDocument ensureSingleDocument(long projectId, long userId, String sourceId, String name,
                                              String text, String summary) {
        long documentId = documents.findDocumentId(projectId, "MANUAL_TEXT", sourceId)
                .orElseGet(() -> documents.createDocument(projectId, "MANUAL_TEXT", sourceId, name, null, userId));
        DocumentRepository.LatestVersion latest = documents.latestVersion(documentId).orElse(null);
        long versionId;
        if (latest == null) {
            versionId = documents.createVersion(documentId, sha256(text), text, "READY");
            documents.createChunk(versionId, 0, "1. 핵심 인사이트", text);
        } else {
            versionId = latest.id();
        }
        documents.updateSummary(versionId, summary);
        documents.markEmbeddingStatus(versionId, "READY", null, false);
        return new SeedDocument(documentId, versionId, versionId);
    }

    private void ensureArchivedDocument(long projectId, long adminId) {
        String sourceId = "demo:archived-guide";
        long id = documents.findDocumentId(projectId, "MANUAL_TEXT", sourceId)
                .orElseGet(() -> documents.createDocument(projectId, "MANUAL_TEXT", sourceId,
                        "촬영용 이전 온보딩 가이드", null, adminId));
        if (documents.latestVersion(id).isEmpty()) {
            String text = "이 문서는 이전 온보딩 기준으로 현재는 보관된 촬영용 자료입니다.";
            long version = documents.createVersion(id, sha256(text), text, "READY");
            documents.createChunk(version, 0, "이전 기준", text);
            documents.updateSummary(version, "이전 온보딩 기준 보관 문서입니다.");
            documents.markEmbeddingStatus(version, "READY", null, false);
        }
        jdbc.update("UPDATE document SET archived=TRUE,archived_at=COALESCE(archived_at,CURRENT_TIMESTAMP),source_deleted=FALSE WHERE id=?", id);
    }

    private SeedMeeting ensureMeeting(long projectId, long memberId) {
        String audioHash = sha256("hub-real-demo-weekly-meeting");
        long meetingId = meetings.findByAudioHash(projectId, audioHash).orElseGet(() ->
                meetings.create(projectId, "촬영용 주간 제품 회의", "demo://weekly-product-meeting.webm",
                        "weekly-product-meeting.webm", "audio/webm", audioHash,
                        OffsetDateTime.now().minusDays(1), LocalDate.now().minusDays(1), memberId));
        String transcript = "이승현: 베타 공개 일정은 9월 27일로 조정하겠습니다. " +
                "박준호: 검색 결과 카드에 원문 출처와 위치를 추가하겠습니다. " +
                "이승현: 완료되면 완료 요청을 올려 주세요. 제가 검토 후 승인하겠습니다. " +
                "박준호: 디자인 검토가 필요한 부분은 도움 요청으로 공유하겠습니다.";
        meetings.completeTranscript(meetingId, transcript);

        List<Long> segmentIds = jdbc.query("SELECT id FROM transcript_segment WHERE meeting_id=? ORDER BY segment_index",
                (rs, n) -> rs.getLong(1), meetingId);
        if (segmentIds.isEmpty()) {
            long s1 = meetings.createSegment(meetingId, 0, 0L, 8000L, "이승현",
                    "베타 공개 일정은 9월 27일로 조정하겠습니다.");
            meetings.createSegment(meetingId, 1, 8000L, 18000L, "박준호",
                    "검색 결과 카드에 원문 출처와 위치를 추가하겠습니다.");
            meetings.createSegment(meetingId, 2, 18000L, 30000L, "이승현",
                    "완료되면 완료 요청을 올려 주세요. 제가 검토 후 승인하겠습니다.");
            segmentIds = List.of(s1);
        }
        return new SeedMeeting(meetingId, segmentIds.get(0));
    }

    private SeedDocument ensureMeetingTranscript(long projectId, long memberId, SeedMeeting meeting) {
        String sourceId = "meeting:" + meeting.id();
        String text = "이승현: 베타 공개 일정은 9월 27일로 조정하겠습니다.\n" +
                "박준호: 검색 결과 카드에 원문 출처와 위치를 추가하겠습니다.\n" +
                "이승현: 완료되면 완료 요청을 올려 주세요. 제가 검토 후 승인하겠습니다.";
        long documentId = documents.findDocumentId(projectId, "MEETING_TRANSCRIPT", sourceId)
                .orElseGet(() -> documents.createDocument(projectId, "MEETING_TRANSCRIPT", sourceId,
                        "촬영용 주간 제품 회의", null, memberId));
        DocumentRepository.LatestVersion latest = documents.latestVersion(documentId).orElse(null);
        long versionId;
        if (latest == null) {
            versionId = documents.createVersion(documentId, sha256(text), text, "READY");
            documents.createChunk(versionId, 0, "회의 요약", text);
        } else {
            versionId = latest.id();
        }
        documents.updateSummary(versionId, "베타 일정 변경, 검색 결과 출처 표시, 담당자의 완료 요청과 관리자 승인 흐름을 논의했습니다.");
        documents.markEmbeddingStatus(versionId, "READY", null, false);
        return new SeedDocument(documentId, versionId, versionId);
    }

    private long ensureCandidateTodo(long projectId, SeedMeeting meeting, long versionId, long memberId) {
        String title = "[촬영] 회의 후 베타 일정 공지";
        Long existing = findTodo(projectId, title);
        long id = existing != null ? existing : todos.create(projectId, versionId, meeting.id(), title,
                "회의에서 변경된 베타 일정을 사내 사용자에게 공지합니다.",
                "박준호", memberId, "박준호", null, LocalDate.now().plusDays(2),
                "HIGH", null, null);
        jdbc.update("""
                UPDATE todo
                SET source_document_version_id=?,source_meeting_id=?,title=?,description=?,
                    assignee_id=NULL,assignee_text='박준호',assignee_suggestion_id=?,assignee_suggestion_text='박준호',
                    due_date=NULL,due_date_suggestion=?,confidence='HIGH',review_status='AI_GENERATED',
                    task_status='TODO',assignment_status='ACTIVE',confirmed_by=NULL,confirmed_at=NULL,
                    pending_approval=FALSE,status_note=NULL,deleted_at=NULL,deleted_by=NULL,updated_at=CURRENT_TIMESTAMP
                WHERE id=?
                """, versionId, meeting.id(), title, "회의에서 변경된 베타 일정을 사내 사용자에게 공지합니다.",
                memberId, java.sql.Date.valueOf(LocalDate.now().plusDays(2)), id);
        return id;
    }

    private long ensureConfirmedTodo(long projectId, long assigneeId, long adminId, String title, String description, LocalDate dueDate) {
        Long existing = findTodo(projectId, title);
        if (existing != null) {
            String assignee = users.findById(assigneeId).orElseThrow().displayName();
            jdbc.update("""
                    UPDATE todo
                    SET title=?,description=?,assignee_id=?,assignee_text=?,due_date=?,review_status='CONFIRMED',
                        assignment_status='ACTIVE',confirmed_by=?,confirmed_at=COALESCE(confirmed_at,CURRENT_TIMESTAMP),
                        deleted_at=NULL,deleted_by=NULL,updated_at=CURRENT_TIMESTAMP
                    WHERE id=?
                    """, title, description, assigneeId, assignee, java.sql.Date.valueOf(dueDate), adminId, existing);
            return existing;
        }
        String assignee = users.findById(assigneeId).orElseThrow().displayName();
        long id = todos.createConfirmed(projectId, null, title, assigneeId, assignee, dueDate, adminId);
        jdbc.update("UPDATE todo SET description=? WHERE id=?", description, id);
        return id;
    }

    private void resetBlockedTodo(long todoId, String note) {
        jdbc.update("""
                UPDATE todo SET task_status='BLOCKED',pending_approval=FALSE,status_note=?,assignment_status='ACTIVE',
                    deleted_at=NULL,deleted_by=NULL,updated_at=CURRENT_TIMESTAMP WHERE id=?
                """, note, todoId);
    }

    private void resetPendingApproval(long todoId) {
        jdbc.update("""
                UPDATE todo SET task_status='IN_PROGRESS',pending_approval=TRUE,status_note=NULL,assignment_status='ACTIVE',
                    deleted_at=NULL,deleted_by=NULL,updated_at=CURRENT_TIMESTAMP WHERE id=?
                """, todoId);
    }

    private void resetReassignment(long todoId, long projectId, long formerId, long adminId) {
        jdbc.update("""
                UPDATE todo SET assignee_id=?,assignee_text='김민수',task_status='TODO',review_status='CONFIRMED',
                    assignment_status='REASSIGNMENT_REQUIRED',pending_approval=FALSE,status_note=NULL,
                    deleted_at=NULL,deleted_by=NULL,updated_at=CURRENT_TIMESTAMP WHERE id=?
                """, formerId, todoId);
        jdbc.update("UPDATE todo_reassignment SET status='RESOLVED',resolved_by=?,resolved_at=CURRENT_TIMESTAMP WHERE todo_id=? AND status='PENDING'",
                adminId, todoId);
        reassignments.enqueue(todoId, projectId, formerId, "기존 담당자가 프로젝트에서 빠져 새 담당자를 지정해야 합니다.", adminId);
    }

    private long ensureDecision(long projectId, long versionId, long meetingId) {
        String statement = "첫 베타는 초대 팀 대상으로 운영하고 검색 결과에 원문 근거를 표시합니다.";
        List<Long> ids = jdbc.query("""
                SELECT id FROM decision_candidate WHERE project_id=? AND statement=? ORDER BY id LIMIT 1
                """, (rs, n) -> rs.getLong(1), projectId, statement);
        long id = ids.isEmpty() ? decisions.create(projectId, versionId, meetingId, statement, "HIGH") : ids.get(0);
        jdbc.update("UPDATE decision_candidate SET review_status='AI_GENERATED',confirmed_by=NULL,confirmed_at=NULL WHERE id=?", id);
        return id;
    }

    private long ensureChange(long projectId, long adminId, long beforeVersion, long afterVersion) {
        List<Long> ids = jdbc.query("""
                SELECT ci.id FROM change_item ci JOIN change_analysis ca ON ca.id=ci.analysis_id
                WHERE ca.project_id=? AND ca.before_version_id=? AND ca.after_version_id=?
                ORDER BY ci.id LIMIT 1
                """, (rs, n) -> rs.getLong(1), projectId, beforeVersion, afterVersion);
        long itemId;
        if (ids.isEmpty()) {
            long analysisId = changes.createAnalysis(projectId, beforeVersion, afterVersion, adminId);
            itemId = changes.addItem(analysisId, "SCHEDULE", "9월 20일 전체 공개", "9월 27일 초대 팀 베타",
                    "사용성 테스트 일정과 단계적 공개 방침을 반영했습니다.");
        } else {
            itemId = ids.get(0);
        }
        jdbc.update("UPDATE change_item SET review_status='AI_GENERATED' WHERE id=?", itemId);
        return itemId;
    }

    private void ensureEvidence(long todoId, long decisionId, long changeId, SeedDocument plan, SeedMeeting meeting) {
        if (!hasLink("todo_evidence", "todo_id", todoId)) {
            long ev = evidence.createTranscriptEvidence(meeting.firstSegmentId(),
                    "베타 공개 일정은 9월 27일로 조정하겠습니다.", sha256("meeting-evidence"));
            evidence.linkTodo(todoId, ev);
        }
        if (!hasLink("decision_evidence", "decision_id", decisionId)) {
            Long chunkId = jdbc.queryForObject(
                    "SELECT id FROM document_chunk WHERE version_id=? ORDER BY chunk_index LIMIT 1", Long.class, plan.versionId());
            long ev = evidence.createDocumentEvidence(plan.versionId(), chunkId,
                    "첫 베타는 초대된 사내 팀을 대상으로 운영합니다.", sha256("decision-evidence"));
            decisions.linkEvidence(decisionId, ev);
        }
        if (!hasLink("change_evidence", "change_item_id", changeId)) {
            Long beforeChunk = jdbc.queryForObject(
                    "SELECT id FROM document_chunk WHERE version_id=? ORDER BY chunk_index LIMIT 1", Long.class, plan.firstVersionId());
            Long afterChunk = jdbc.queryForObject(
                    "SELECT id FROM document_chunk WHERE version_id=? ORDER BY chunk_index LIMIT 1", Long.class, plan.versionId());
            long beforeEv = evidence.createDocumentEvidence(plan.firstVersionId(), beforeChunk,
                    "9월 20일 전체 공개를 목표로 합니다.", sha256("change-before"));
            long afterEv = evidence.createDocumentEvidence(plan.versionId(), afterChunk,
                    "베타 공개 일정은 9월 27일로 조정합니다.", sha256("change-after"));
            changes.linkEvidence(changeId, beforeEv, "BEFORE");
            changes.linkEvidence(changeId, afterEv, "AFTER");
        }
    }

    private boolean hasLink(String table, String column, long id) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + column + "=?", Long.class, id);
        return count != null && count > 0;
    }

    private void ensureSuccessfulJob(long projectId, String jobType, String targetType, long targetId,
                                     String requestKey, String resultJson) {
        ProcessingJobRepository.Lease lease = jobs.createOrReuse(projectId, jobType, targetType, targetId, requestKey);
        if (lease.shouldRun()) jobs.start(lease.id());
        jobs.success(lease.id(), resultJson);
    }

    private void ensureSearchRule(long projectId, long adminId) {
        boolean exists = searchRules.list(projectId, false).stream().anyMatch(r -> "베타/런칭 기준".equals(r.name()));
        if (!exists) {
            searchRules.create(projectId, "베타/런칭 기준",
                    List.of("출시", "런칭", "베타 일정"), List.of("*베타*", "*런칭*", "*일정*"),
                    "촬영용 Atlas 베타 운영 계획", "SMART", 200, true, adminId);
        }
    }

    private void ensureSpreadsheet(long projectId, long adminId) {
        boolean exists = sheets.listForProject(projectId).stream().anyMatch(f -> "촬영용 런칭 체크리스트".equals(f.name()));
        if (exists) return;
        long fileId = sheets.createFile(projectId, "촬영용 런칭 체크리스트", adminId,
                "[{\"key\":\"c1\",\"label\":\"업무\"},{\"key\":\"c2\",\"label\":\"담당자\"},{\"key\":\"c3\",\"label\":\"상태\"}]",
                null, null);
        sheets.createRow(fileId, 0, "{\"c1\":\"검색 결과 출처 표시\",\"c2\":\"박준호\",\"c3\":\"진행 중\"}");
        sheets.createRow(fileId, 1, "{\"c1\":\"베타 일정 공지\",\"c2\":\"박준호\",\"c3\":\"AI 검토 대기\"}");
        sheets.createRow(fileId, 2, "{\"c1\":\"배포 체크리스트 검수\",\"c2\":\"영상용 관리자\",\"c3\":\"승인 대기\"}");
    }

    private void ensureExternalDemoItems(long projectId) {
        connectors.saveItem(projectId, "GITHUB", "demo-pr-27", "GIT_PR",
                "[DEMO] 검색 결과 출처 배지 개선",
                "검색 결과 카드에 GitHub/문서/회의 출처와 원문 위치를 표시하는 변경입니다.",
                "박준호", "https://github.com/chl4890620123-collab/hub/pull/27",
                OffsetDateTime.now().minusDays(1), "{\"demo\":true}");
        connectors.saveItem(projectId, "GITHUB", "demo-commit-search", "GIT_COMMIT",
                "[DEMO] 통합 검색 분류 개선",
                "첨부파일과 Git 변경 알림을 문서 목록과 알림 영역에서 명확히 구분합니다.",
                "박준호", "https://github.com/chl4890620123-collab/hub",
                OffsetDateTime.now().minusDays(2), "{\"demo\":true}");
    }

    private void ensureSecurityTerms(long adminId) {
        sensitiveTerms.add("민감계약금액", adminId);
        sensitiveTerms.add("고객전화번호", adminId);
    }

    private void ensureTimeline(long projectId, long candidateTodoId, long approvalTodoId, long documentId) {
        if (count("timeline_event", projectId) > 0 && hasDemoTimeline(projectId)) return;
        timeline.append(projectId, "DOCUMENT_IMPORTED", "촬영용 Atlas 베타 운영 계획",
                "베타 일정과 검색 원칙을 등록했습니다.", LocalDateTime.now().minusHours(4), "DOCUMENT", documentId);
        timeline.append(projectId, "TODO_CREATED", "[촬영] 회의 후 베타 일정 공지",
                "AI가 회의에서 후속 할 일을 찾았습니다.", LocalDateTime.now().minusHours(3), "TODO", candidateTodoId);
        timeline.append(projectId, "TODO_COMPLETION_REQUESTED", "[촬영] 배포 체크리스트 최종 검수",
                "박준호님이 관리자 검토를 요청했습니다.", LocalDateTime.now().minusHours(1), "TODO", approvalTodoId);
    }

    private void ensureAudit(long adminId, long memberId, long projectId, long candidateTodoId, long approvalTodoId) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM audit_log WHERE project_id=? AND detail_json LIKE '%DEMO_SEED%'
                """, Long.class, projectId);
        if (count != null && count > 0) return;
        audit.add(adminId, projectId, "PROJECT_MEMBER_JOIN", "USER", memberId, "{\"source\":\"DEMO_SEED\"}");
        audit.add(memberId, projectId, "TODO_COMPLETION_REQUESTED", "TODO", approvalTodoId, "{\"source\":\"DEMO_SEED\"}");
        audit.add(adminId, projectId, "TODO_CREATED", "TODO", candidateTodoId, "{\"source\":\"DEMO_SEED\"}");
    }

    private boolean hasDemoTimeline(long projectId) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM timeline_event WHERE project_id=? AND title LIKE '[촬영]%'
                """, Long.class, projectId);
        return count != null && count > 0;
    }

    private long count(String table, long projectId) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE project_id=?", Long.class, projectId);
        return count == null ? 0 : count;
    }

    private Long findTodo(long projectId, String title) {
        List<Long> ids = jdbc.query("SELECT id FROM todo WHERE project_id=? AND title=? ORDER BY id LIMIT 1",
                (rs, n) -> rs.getLong(1), projectId, title);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private static String planText() {
        return "첫 베타는 초대된 사내 팀을 대상으로 운영합니다. " +
                "베타 공개 일정은 9월 27일로 조정하며 사용성 테스트 결과를 반영합니다. " +
                "검색 결과에는 원문 출처, 위치, 최신 동기화 시간을 함께 표시합니다. " +
                "담당자는 작업이 끝나면 완료 요청을 올리고 관리자가 검토 후 승인합니다.";
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String cleanLogin(String value, String fallback) {
        String clean = value == null || value.isBlank() ? fallback : value.trim();
        if (clean.length() <= 40) return clean;
        return clean.substring(0, 40);
    }

    private static String derivedLogin(String base, String suffix) {
        int room = Math.max(1, 40 - suffix.length() - 1);
        String prefix = base.length() > room ? base.substring(0, room) : base;
        return prefix + "-" + suffix;
    }

    private record SeedDocument(long documentId, long firstVersionId, long versionId) {}
    private record SeedMeeting(long id, long firstSegmentId) {}
}
