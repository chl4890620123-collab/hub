// one password policy shared by signup, bootstrap, resets, and password changes.
package com.hub.service;

import org.springframework.stereotype.Component;

@Component
public class PasswordPolicy {
    public void validate(String password) {
        if (password == null || password.length() < 12) {
            throw new IllegalArgumentException("비밀번호는 12자 이상으로 입력해 주세요.");
        }
        boolean letter = password.chars().anyMatch(Character::isLetter);
        boolean digit = password.chars().anyMatch(Character::isDigit);
        if (!letter || !digit) {
            throw new IllegalArgumentException("비밀번호에는 영문과 숫자를 모두 포함해 주세요.");
        }
    }
}
