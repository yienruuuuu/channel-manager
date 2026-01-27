package io.github.yienruuuuu.repository;

import io.github.yienruuuuu.bean.entity.PaymentRecord;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * @author Eric.Lee
 * Date: 2026/01/27
 */
public interface PaymentRecordRepository extends JpaRepository<PaymentRecord, String> {
    PaymentRecord findByTelegramPaymentChargeId(String telegramPaymentChargeId);
}
