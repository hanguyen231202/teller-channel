package com.tellerchannel.channel.reconciliation;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Chạy đối soát định kỳ. fixedDelay: lượt sau bắt đầu khi lượt trước XONG + 30s, không chồng lượt.
 *
 * Lưu ý khi chạy nhiều instance: mỗi instance đều chạy job này. Cần khóa phân tán (vd. ShedLock)
 * hoặc SELECT ... FOR UPDATE SKIP LOCKED để hai instance không đối soát cùng giao dịch.
 * Ở đây vẫn an toàn nhờ @Version (instance chậm hơn sẽ lỗi optimistic lock), chỉ tốn thêm một lần hỏi core.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "teller.reconciliation.enabled", havingValue = "true", matchIfMissing = true)
public class ReconciliationJob {

    private final ReconciliationService reconciliationService;

    public ReconciliationJob(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @Scheduled(fixedDelayString = "${teller.reconciliation.fixed-delay:PT30S}",
            initialDelayString = "${teller.reconciliation.fixed-delay:PT30S}")
    public void run() {
        reconciliationService.reconcileBatch();
    }
}
