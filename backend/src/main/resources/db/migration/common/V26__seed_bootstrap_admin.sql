-- Admin signup no longer self-approves (not even the first one) - every admin signup lands PENDING
-- and needs an existing admin to approve it (see SignupService.registerAdmin). That leaves a fresh
-- install with zero admins unable to ever get one, since nobody exists to approve the first request.
-- This migration seeds exactly one bootstrap admin, but only when no approved admin exists yet - on
-- every install that already has one, the WHERE NOT EXISTS guard makes this a no-op, so it is safe to
-- ship unconditionally and it runs on every startup like any other Flyway migration.
--
-- Bootstrap login: admin / ChangeMe123!Hub
-- must_change_password=TRUE forces a real password to be chosen on first login before anything else
-- is reachable (see CurrentUserService.requireOperational). Change the password immediately, then use
-- this account to approve everyone else's pending admin/member signups.
INSERT INTO app_user(login_id,email,password_hash,display_name,global_role,requested_role,account_status,
                     must_change_password,approval_status,approved_at,privacy_consent_at)
SELECT 'admin','admin@hub.local','$2b$12$1TLGYgtdT9piJ7jwFqVcLuj0nbRYd67ScTCtDsDvUQh/dqJTH8SFq','시스템 관리자',
       'ADMIN','ADMIN','ACTIVE',TRUE,'APPROVED',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM app_user WHERE approval_status='APPROVED' AND global_role='ADMIN');
