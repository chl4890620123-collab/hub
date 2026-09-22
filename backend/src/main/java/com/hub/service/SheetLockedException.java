package com.hub.service;

/** Thrown when a password-protected spreadsheet file is opened/edited without the right password. */
public class SheetLockedException extends RuntimeException {
    private final String hint;

    public SheetLockedException(String hint) {
        super(hint == null ? "비밀번호가 필요합니다." : "비밀번호가 필요합니다. 힌트: " + hint);
        this.hint = hint;
    }

    public String hint() { return hint; }
}
