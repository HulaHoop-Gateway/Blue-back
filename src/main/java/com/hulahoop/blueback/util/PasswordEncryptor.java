package com.hulahoop.blueback.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class PasswordEncryptor {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String rawPassword = "admin01"; // 암호화할 평문 비밀번호
        String encodedPassword = encoder.encode(rawPassword);

        System.out.println("BCrypt 암호화된 비밀번호: " + encodedPassword);
    }
}
