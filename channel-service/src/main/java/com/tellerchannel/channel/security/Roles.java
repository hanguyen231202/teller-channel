package com.tellerchannel.channel.security;

/** Vai trò trong hệ thống. Spring Security thêm tiền tố ROLE_ khi kiểm tra hasRole(...). */
public final class Roles {

    /** Giao dịch viên (maker). */
    public static final String TELLER = "TELLER";
    /** Kiểm soát viên (checker). */
    public static final String SUPERVISOR = "SUPERVISOR";

    private Roles() {
    }
}
