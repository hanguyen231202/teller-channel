package com.tellerchannel.coremock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;

/** Giữ cấu hình chaos hiện tại; đổi được lúc đang chạy qua /core/admin/chaos, không cần restart. */
@Service
public class ChaosService {

    private static final Logger log = LoggerFactory.getLogger(ChaosService.class);

    private final ChaosSettings initial;
    private final AtomicReference<ChaosSettings> current;

    public ChaosService(ChaosSettings initial) {
        this.initial = initial;
        this.current = new AtomicReference<>(initial);
    }

    public ChaosSettings get() {
        return current.get();
    }

    public void update(ChaosSettings settings) {
        log.warn("Chaos settings changed: {}", settings);
        current.set(settings);
    }

    public void reset() {
        update(initial);
    }

    /** Gọi đầu mỗi lệnh hạch toán. */
    public void beforePosting() {
        ChaosSettings s = current.get();
        maybeFail(s.postingFailureRate());
        if (s.postingDelayMs() > 0) {
            log.warn("Chaos: delaying posting by {} ms", s.postingDelayMs());
            sleep(s.postingDelayMs());
        }
    }

    /** Gọi đầu mỗi API tra cứu. */
    public void beforeInquiry() {
        maybeFail(current.get().inquiryFailureRate());
    }

    private static void maybeFail(double rate) {
        if (rate > 0 && ThreadLocalRandom.current().nextDouble() < rate) {
            throw new CoreUnavailableException();
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
