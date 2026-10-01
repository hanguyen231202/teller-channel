package com.tellerchannel.coremock;

import java.math.BigDecimal;

public record Account(String accountNo, String accountName, String currency, BigDecimal balance) {

}
