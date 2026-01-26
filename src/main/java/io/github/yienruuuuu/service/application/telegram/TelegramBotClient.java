package io.github.yienruuuuu.service.application.telegram;

import io.github.yienruuuuu.bean.entity.Bot;
import io.github.yienruuuuu.repository.BotRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendAnimation;
import org.telegram.telegrambots.meta.api.methods.send.SendAudio;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMediaGroup;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.send.SendVideo;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Eric.Lee
 * Date: 2026/01/23
 */
@Component
@Slf4j
public class TelegramBotClient {
    private final BotRepository botRepository;
    private final Map<Integer, TelegramClient> clientCache = new ConcurrentHashMap<>();

    /**
     * 建立 TelegramBotClient，負責管理 TelegramClient 快取。
     *
     * @param botRepository Bot 資料存取物件
     */
    public TelegramBotClient(BotRepository botRepository) {
        this.botRepository = botRepository;
    }


    /**
     * 通用的 send 方法，支援所有 BotApiMethod 的子類別。
     *
     * @param method Telegram API 方法
     * @param bot    目標 Bot
     * @param <T>    回傳型別
     * @param <Method> 方法型別
     * @return API 回傳結果，失敗時回傳 null
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
     * 發送媒體群組（相簿）訊息。
     *
     * @param method SendMediaGroup 方法
     * @param bot    目標 Bot
     * @return 回傳的訊息列表，失敗時回傳 null
     */
    public List<Message> send(SendMediaGroup method, Bot bot) {
        TelegramClient telegramClient = getOrCreateTelegramClient(bot);
        try {
            return telegramClient.execute(method);
        } catch (TelegramApiException e) {
            handleException(e, method.getMethod());
            return null;
        }
    }

    /**
     * 發送圖片訊息。
     *
     * @param method SendPhoto 方法
     * @param bot    目標 Bot
     * @return 回傳的訊息，失敗時回傳 null
     */
    public Message send(SendPhoto method, Bot bot) {
        TelegramClient telegramClient = getOrCreateTelegramClient(bot);
        try {
            return telegramClient.execute(method);
        } catch (TelegramApiException e) {
            handleException(e, method.getMethod());
            return null;
        }
    }

    /**
     * 發送影片訊息。
     *
     * @param method SendVideo 方法
     * @param bot    目標 Bot
     * @return 回傳的訊息，失敗時回傳 null
     */
    public Message send(SendVideo method, Bot bot) {
        TelegramClient telegramClient = getOrCreateTelegramClient(bot);
        try {
            return telegramClient.execute(method);
        } catch (TelegramApiException e) {
            handleException(e, method.getMethod());
            return null;
        }
    }

    /**
     * 發送文件訊息。
     *
     * @param method SendDocument 方法
     * @param bot    目標 Bot
     * @return 回傳的訊息，失敗時回傳 null
     */
    public Message send(SendDocument method, Bot bot) {
        TelegramClient telegramClient = getOrCreateTelegramClient(bot);
        try {
            return telegramClient.execute(method);
        } catch (TelegramApiException e) {
            handleException(e, method.getMethod());
            return null;
        }
    }

    /**
     * 發送音訊訊息。
     *
     * @param method SendAudio 方法
     * @param bot    目標 Bot
     * @return 回傳的訊息，失敗時回傳 null
     */
    public Message send(SendAudio method, Bot bot) {
        TelegramClient telegramClient = getOrCreateTelegramClient(bot);
        try {
            return telegramClient.execute(method);
        } catch (TelegramApiException e) {
            handleException(e, method.getMethod());
            return null;
        }
    }

    /**
     * 發送動圖訊息。
     *
     * @param method SendAnimation 方法
     * @param bot    目標 Bot
     * @return 回傳的訊息，失敗時回傳 null
     */
    public Message send(SendAnimation method, Bot bot) {
        TelegramClient telegramClient = getOrCreateTelegramClient(bot);
        try {
            return telegramClient.execute(method);
        } catch (TelegramApiException e) {
            handleException(e, method.getMethod());
            return null;
        }
    }

    /**
     * 從快取中取得 TelegramClient，若不存在則建立並快取。
     *
     * @param bot Bot 實體
     * @return TelegramClient
     */
    private TelegramClient getOrCreateTelegramClient(Bot bot) {
        return clientCache.computeIfAbsent(bot.getId(), this::createTelegramClient);
    }

    /**
     * 依據 botId 建立 TelegramClient。
     *
     * @param botId Bot 主鍵
     * @return TelegramClient
     */
    private TelegramClient createTelegramClient(Integer botId) {
        Bot bot = botRepository.findBotById(botId);
        log.info("創建 TelegramClient for botId: {}", botId);
        return new OkHttpTelegramClient(bot.getBotToken());
    }

    /**
     * 統一處理 Telegram API 例外。
     *
     * @param e      例外
     * @param action 呼叫的方法名稱
     */
    private void handleException(TelegramApiException e, String action) {
        String message = e.getMessage();
        if (message != null && message.contains("message is not modified")) {
            log.debug("{} 無需更新: {}", action, message);
            return;
        }
        log.error("{} 操作失敗: ", action, e);
    }
}
