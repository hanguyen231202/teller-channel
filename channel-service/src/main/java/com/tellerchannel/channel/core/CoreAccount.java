package com.tellerchannel.channel.core;

import java.math.BigDecimal;

/** Thông tin tài khoản lấy trực tiếp từ core. Kênh không lưu lại số dư này. */
public record CoreAccount(String accountNo, String accountName, String currency, BigDecimal balance, String status) {
}
