package io.github.yienruuuuu.service.application.telegram.main_bot;

import io.github.yienruuuuu.bean.entity.Bot;
import io.github.yienruuuuu.bean.enums.BotType;
import io.github.yienruuuuu.config.AppConfig;
import io.github.yienruuuuu.service.application.telegram.TelegramBotClient;
import io.github.yienruuuuu.service.business.BotService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.ForwardMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

/**
 * @author Eric.Lee Date: 2024/10/16
 */

@Component
@Slf4j
public class MainBotConsumer implements LongPollingSingleThreadUpdateConsumer {
    private final BotService botService;
    private final AppConfig appConfig;
    private final TelegramBotClient telegramBotClient;


    @Autowired
    public MainBotConsumer(BotService botService, AppConfig appConfig, TelegramBotClient telegramBotClient) {
        this.botService = botService;
        this.appConfig = appConfig;
        this.telegramBotClient = telegramBotClient;
    }

    @Override
    public void consume(Update update) {
        if (!update.hasChannelPost()) {
            return;
        }

        String sourceChannelId = String.valueOf(update.getChannelPost().getChatId());
        if (!sourceChannelId.equals(appConfig.getBotCommunicateChannelChatId())) {
            return;
        }

        Bot mainBotEntity = botService.findByBotType(BotType.MAIN);
        ForwardMessage msg = ForwardMessage.builder()
                .chatId(appConfig.getBotPublicChannelId())
                .fromChatId(appConfig.getBotCommunicateChannelChatId())
                .messageId(update.getChannelPost().getMessageId())
                .build();
        telegramBotClient.send(msg, mainBotEntity);
        log.info("已轉發訊息 {} 從 {} 到 {}", update.getChannelPost().getMessageId(), sourceChannelId, appConfig.getBotPublicChannelId());
    }
}

