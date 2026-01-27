package io.github.yienruuuuu.service.business.impl;

import io.github.yienruuuuu.bean.entity.PaymentRecord;
import io.github.yienruuuuu.repository.PaymentRecordRepository;
import io.github.yienruuuuu.service.business.PaymentRecordService;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * @author Eric.Lee
 * Date: 2026/01/27
 */
@Service("paymentRecordService")
public class PaymentRecordServiceImpl implements PaymentRecordService {
    private final PaymentRecordRepository paymentRecordRepository;

    public PaymentRecordServiceImpl(PaymentRecordRepository paymentRecordRepository) {
        this.paymentRecordRepository = paymentRecordRepository;
    }

    @Override
    public PaymentRecord save(PaymentRecord record) {
        return paymentRecordRepository.save(record);
    }

    @Override
    public PaymentRecord findByTelegramChargeId(String telegramPaymentChargeId) {
        return paymentRecordRepository.findByTelegramPaymentChargeId(telegramPaymentChargeId);
    }

    @Override
    public void markRefunded(String telegramPaymentChargeId) {
        PaymentRecord record = paymentRecordRepository.findByTelegramPaymentChargeId(telegramPaymentChargeId);
        if (record == null) {
            return;
        }
        record.setStatus("REFUNDED");
        record.setRefundedAt(Instant.now());
        paymentRecordRepository.save(record);
    }
}
