package io.github.yienruuuuu.service.business;

import io.github.yienruuuuu.bean.entity.PaymentRecord;

/**
 * @author Eric.Lee
 * Date: 2026/01/27
 */
public interface PaymentRecordService {
    PaymentRecord save(PaymentRecord record);

    PaymentRecord findByTelegramChargeId(String telegramPaymentChargeId);

    void markRefunded(String telegramPaymentChargeId);
}
