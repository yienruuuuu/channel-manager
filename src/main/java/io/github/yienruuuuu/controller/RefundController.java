package io.github.yienruuuuu.controller;

import io.github.yienruuuuu.bean.entity.Bot;
import io.github.yienruuuuu.bean.entity.PaymentRecord;
import io.github.yienruuuuu.bean.enums.BotType;
import io.github.yienruuuuu.service.application.telegram.TelegramBotClient;
import io.github.yienruuuuu.service.business.BotService;
import io.github.yienruuuuu.service.business.PaymentRecordService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.telegram.telegrambots.meta.api.methods.payments.RefundStarPayment;

/**
 * @author Eric.Lee
 * Date: 2026/01/27
 */
@RestController
@RequestMapping("/api/admin")
public class RefundController {
    private final PaymentRecordService paymentRecordService;
    private final BotService botService;
    private final TelegramBotClient telegramBotClient;

    public RefundController(
            PaymentRecordService paymentRecordService,
            BotService botService,
            TelegramBotClient telegramBotClient
    ) {
        this.paymentRecordService = paymentRecordService;
        this.botService = botService;
        this.telegramBotClient = telegramBotClient;
    }

    @PostMapping("/refunds/stars")
    public ResponseEntity<RefundResponse> refundStars(@RequestBody RefundRequest request) {
        if (request == null || request.telegramPaymentChargeId == null || request.telegramPaymentChargeId.isBlank()) {
            return ResponseEntity.badRequest().body(new RefundResponse("INVALID_REQUEST", "telegramPaymentChargeId is required"));
        }
        PaymentRecord record = paymentRecordService.findByTelegramChargeId(request.telegramPaymentChargeId);
        if (record == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new RefundResponse("NOT_FOUND", "payment record not found"));
        }
        if ("REFUNDED".equalsIgnoreCase(record.getStatus())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new RefundResponse("ALREADY_REFUNDED", "payment already refunded"));
        }
        Bot cashierBot = botService.findByBotType(BotType.CASHIER);
        if (cashierBot == null) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new RefundResponse("BOT_NOT_FOUND", "cashier bot not found"));
        }
        RefundStarPayment refund = RefundStarPayment.builder()
                .userId(record.getUserId())
                .telegramPaymentChargeId(record.getTelegramPaymentChargeId())
                .build();
        Boolean ok = telegramBotClient.send(refund, cashierBot);
        if (ok == null || !ok) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(new RefundResponse("REFUND_FAILED", "telegram refund failed"));
        }
        paymentRecordService.markRefunded(record.getTelegramPaymentChargeId());
        return ResponseEntity.ok(new RefundResponse("OK", "refunded"));
    }

    public static class RefundRequest {
        public String telegramPaymentChargeId;
    }

    public static class RefundResponse {
        public String code;
        public String message;

        public RefundResponse(String code, String message) {
            this.code = code;
            this.message = message;
        }
    }
}
