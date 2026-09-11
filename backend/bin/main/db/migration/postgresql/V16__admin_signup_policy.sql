-- ADMIN signup is self-service; only MEMBER signup requires approval.
UPDATE app_user
SET approval_status='APPROVED',
    account_status='ACTIVE',
    global_role='ADMIN',
    approved_at=COALESCE(approved_at, CURRENT_TIMESTAMP),
    rejected_at=NULL,
    rejection_reason=NULL
WHERE requested_role='ADMIN' AND approval_status='PENDING';