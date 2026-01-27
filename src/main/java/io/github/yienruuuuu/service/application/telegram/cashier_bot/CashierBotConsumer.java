package io.github.yienruuuuu.service.application.telegram.cashier_bot;

import io.github.yienruuuuu.bean.entity.Bot;
import io.github.yienruuuuu.bean.entity.PaymentRecord;
import io.github.yienruuuuu.bean.enums.BotType;
import io.github.yienruuuuu.config.AppConfig;
import io.github.yienruuuuu.service.application.telegram.TelegramBotClient;
import io.github.yienruuuuu.service.business.BotService;
import io.github.yienruuuuu.service.business.ConfigService;
import io.github.yienruuuuu.service.business.PaymentRecordService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.AnswerPreCheckoutQuery;
import org.telegram.telegrambots.meta.api.methods.groupadministration.CreateChatInviteLink;
import org.telegram.telegrambots.meta.api.methods.invoices.SendInvoice;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.ChatInviteLink;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.payments.LabeledPrice;

import java.util.List;
import java.util.UUID;

/**
 * @author Eric.Lee
 * Date: 2026/01/27
 */
@Component
@Slf4j
public class CashierBotConsumer implements LongPollingSingleThreadUpdateConsumer {
    private static final String CONFIG_STAR_PRICE = "cashier_star_price";
    private static final String STARS_CURRENCY = "XTR";
    private final BotService botService;
    private final ConfigService configService;
    private final AppConfig appConfig;
    private final TelegramBotClient telegramBotClient;
    private final PaymentRecordService paymentRecordService;

    public CashierBotConsumer(
            BotService botService,
            ConfigService configService,
            AppConfig appConfig,
            TelegramBotClient telegramBotClient,
            PaymentRecordService paymentRecordService
    ) {
        this.botService = botService;
        this.configService = configService;
        this.appConfig = appConfig;
        this.telegramBotClient = telegramBotClient;
        this.paymentRecordService = paymentRecordService;
    }

    @Override
    public void consume(Update update) {
        if (update.hasPreCheckoutQuery()) {
            answerPreCheckout(update);
            return;
        }
        if (!update.hasMessage()) {
            return;
        }
        Message message = update.getMessage();
        if (message.getSuccessfulPayment() != null) {
            handleSuccessfulPayment(message);
            return;
        }
        handleIncomingMessage(message);
    }

    private void handleIncomingMessage(Message message) {
        if (message.getChat() == null || !"private".equals(message.getChat().getType())) {
            return;
        }
        if (message.getText() == null) {
            return;
        }
        String text = message.getText().trim();
        if (!"/start".equalsIgnoreCase(text) && !"/paid".equalsIgnoreCase(text)) {
            return;
        }
        Bot cashierBot = botService.findByBotType(BotType.CASHIER);
        if (cashierBot == null) {
            log.warn("未找到 CASHIER BOT，無法發送付款訊息");
            return;
        }
        int price = configService.getIntValue(CONFIG_STAR_PRICE, 50);
        if (price <= 0) {
            sendPlainMessage(cashierBot, message.getChatId(), "目前無法提供付款服務，請稍後再試");
            return;
        }
        SendInvoice invoice = SendInvoice.builder()
                .chatId(String.valueOf(message.getChatId()))
                .title("頻道邀請連結")
                .description("付款後可取得一次性邀請連結")
                .payload(buildPayload(message))
                .providerToken("")
                .currency(STARS_CURRENCY)
                .prices(List.of(new LabeledPrice("Invite Link", price)))
                .build();
        telegramBotClient.send(invoice, cashierBot);
    }

    private void handleSuccessfulPayment(Message message) {
        if (message.getChat() == null || !"private".equals(message.getChat().getType())) {
            return;
        }
        savePaymentRecord(message);
        Bot mainBot = botService.findByBotType(BotType.MAIN);
        Bot cashierBot = botService.findByBotType(BotType.CASHIER);
        if (mainBot == null || cashierBot == null) {
            log.warn("MAIN 或 CASHIER BOT 不存在，無法發送邀請連結");
            return;
        }
        ChatInviteLink inviteLink = createSingleUseInviteLink(mainBot);
        if (inviteLink == null || inviteLink.getInviteLink() == null) {
            sendPlainMessage(cashierBot, message.getChatId(), "邀請連結產生失敗，請稍後再試");
            return;
        }
        String text = "付款完成，請使用以下一次性邀請連結：\n" + inviteLink.getInviteLink();
        sendPlainMessage(cashierBot, message.getChatId(), text);
    }

    private void savePaymentRecord(Message message) {
        if (message.getSuccessfulPayment() == null || message.getFrom() == null) {
            return;
        }
        PaymentRecord record = new PaymentRecord();
        record.setId(UUID.randomUUID().toString());
        record.setUserId(message.getFrom().getId());
        record.setChatId(message.getChatId());
        record.setMessageId(message.getMessageId());
        record.setInvoicePayload(message.getSuccessfulPayment().getInvoicePayload());
        record.setCurrency(message.getSuccessfulPayment().getCurrency());
        record.setTotalAmount(message.getSuccessfulPayment().getTotalAmount());
        record.setTelegramPaymentChargeId(message.getSuccessfulPayment().getTelegramPaymentChargeId());
        record.setProviderPaymentChargeId(message.getSuccessfulPayment().getProviderPaymentChargeId());
        record.setStatus("PAID");
        paymentRecordService.save(record);
    }

    private ChatInviteLink createSingleUseInviteLink(Bot mainBot) {
        String targetChannelId = appConfig.getBotPublicChannelId();
        if (targetChannelId == null || targetChannelId.isBlank()) {
            log.warn("未設定公開頻道，無法建立邀請連結");
            return null;
        }
        CreateChatInviteLink create = CreateChatInviteLink.builder()
                .chatId(targetChannelId)
                .memberLimit(1)
                .build();
        return telegramBotClient.send(create, mainBot);
    }

    private void answerPreCheckout(Update update) {
        AnswerPreCheckoutQuery answer = AnswerPreCheckoutQuery.builder()
                .preCheckoutQueryId(update.getPreCheckoutQuery().getId())
                .ok(true)
                .build();
        Bot cashierBot = botService.findByBotType(BotType.CASHIER);
        if (cashierBot == null) {
            log.warn("未找到 CASHIER BOT，無法回覆付款確認");
            return;
        }
        telegramBotClient.send(answer, cashierBot);
    }

    private void sendPlainMessage(Bot bot, Long chatId, String text) {
        SendMessage sendMessage = SendMessage.builder()
                .chatId(String.valueOf(chatId))
                .text(text)
                .build();
        telegramBotClient.send(sendMessage, bot);
    }

    private String buildPayload(Message message) {
        String userId = message.getFrom() == null ? "unknown" : String.valueOf(message.getFrom().getId());
        return "invite_" + userId + "_" + System.currentTimeMillis();
    }
}
