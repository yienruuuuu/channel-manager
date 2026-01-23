package io.github.yienruuuuu.service.application.telegram;

import io.github.yienruuuuu.bean.entity.Bot;
import io.github.yienruuuuu.repository.BotRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethod;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Eric.Lee
 * Date: 2024/10/18
 */
@Component
@Slf4j
public class TelegramBotClient {
    private final BotRepository botRepository;
    private final Map<Integer, TelegramClient> clientCache = new ConcurrentHashMap<>();

    public TelegramBotClient(BotRepository botRepository) {
        this.botRepository = botRepository;
    }


    /**
     * 通用的 send 方法，支援所有 BotApiMethod 的子類別
     */
    public <T extends Serializable, Method extends BotApiMethod<T>> T send(Method method, Bot bot) {
        TelegramClient telegramClient = getOrCreateTelegramClient(bot);
        try {
            return telegramClient.execute(method);
        } catch (TelegramApiException e) {
            handleException(e, method.getMethod());
            return null;
        }
    }

    /**
     * 從緩存中獲取 TelegramClient，若不存在則創建並緩存
     */
    private TelegramClient getOrCreateTelegramClient(Bot bot) {
        return clientCache.computeIfAbsent(bot.getId(), this::createTelegramClient);
    }

    /**
     * 根據 botId 創建 TelegramClient
     */
    private TelegramClient createTelegramClient(Integer botId) {
        Bot bot = botRepository.findBotById(botId);
        log.info("創建 TelegramClient for botId: {}", botId);
        return new OkHttpTelegramClient(bot.getBotToken());
    }

    /**
     * 將錯誤處理統一管理
     */
    private void handleException(TelegramApiException e, String action) {
        log.error("{} 操作失敗: ", action, e);
    }
}
