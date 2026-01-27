package io.github.yienruuuuu.bean.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * @author Eric.Lee
 * Date: 2026/01/27
 */
@Getter
@Setter
@Entity
@Table(name = "payment_record", schema = "tg_manager_bot")
public class PaymentRecord extends BaseEntity {
    @Id
    @Column(name = "id", nullable = false, length = 36)
    private String id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "chat_id", nullable = false)
    private Long chatId;

    @Column(name = "message_id")
    private Integer messageId;

    @Column(name = "invoice_payload", nullable = false, length = 256)
    private String invoicePayload;

    @Column(name = "currency", nullable = false, length = 8)
    private String currency;

    @Column(name = "total_amount", nullable = false)
    private Integer totalAmount;

    @Column(name = "telegram_payment_charge_id", nullable = false, length = 128)
    private String telegramPaymentChargeId;

    @Column(name = "provider_payment_charge_id", length = 128)
    private String providerPaymentChargeId;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "refunded_at")
    private java.time.Instant refundedAt;
}
