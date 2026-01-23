package io.github.yienruuuuu.service.application.telegram.main_bot;

import io.github.yienruuuuu.bean.entity.Bot;
import io.github.yienruuuuu.bean.enums.BotType;
import io.github.yienruuuuu.config.AppConfig;
import io.github.yienruuuuu.service.application.telegram.TelegramBotClient;
import io.github.yienruuuuu.service.business.BotService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.CopyMessages;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * @author Eric.Lee
 * Date: 2026/01/23
 */

@Component
@Slf4j
public class MainBotConsumer implements LongPollingSingleThreadUpdateConsumer {
    private static final long MEDIA_GROUP_FLUSH_DELAY_MS = 2000L;
    private final BotService botService;
    private final AppConfig appConfig;
    private final TelegramBotClient telegramBotClient;
    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService mediaGroupScheduler = Executors.newSingleThreadScheduledExecutor();
    private final Map<String, MediaGroupBuffer> mediaGroupBuffers = new ConcurrentHashMap<>();

    /**
     * 建立主要更新消費者，注入必要的服務與工具。
     *
     * @param botService Bot 服務
     * @param appConfig  應用設定
     * @param telegramBotClient Telegram API 呼叫封裝
     * @param objectMapper JSON 序列化工具
     */
    @Autowired
    public MainBotConsumer(BotService botService, AppConfig appConfig, TelegramBotClient telegramBotClient, ObjectMapper objectMapper) {
        this.botService = botService;
        this.appConfig = appConfig;
        this.telegramBotClient = telegramBotClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 處理 Telegram 更新：記錄 JSON、個人訊息與頻道訊息，並依規則複製訊息。
     *
     * @param update Telegram 更新
     */
    @Override
    public void consume(Update update) {
        this.logIncomingUpdateJson(update);

        if (!update.hasChannelPost()) {
            return;
        }

        Message channelPost = update.getChannelPost();
        String sourceChannelId = String.valueOf(channelPost.getChatId());
        if (!sourceChannelId.equals(appConfig.getBotCommunicateChannelChatId())) {
            return;
        }

        if (this.isPureText(channelPost)) {
            log.info("略過純文字訊息 {}", channelPost.getMessageId());
            return;
        }

        if (channelPost.getMediaGroupId() != null) {
            this.bufferMediaGroupCopy(channelPost);
            return;
        }

        this.copySingleMessage(channelPost, sourceChannelId);
    }

    /**
     * 以 JSON 格式記錄完整更新內容，方便除錯與追查。
     *
     * @param update Telegram 更新
     */
    private void logIncomingUpdateJson(Update update) {
        try {
            String json = objectMapper.writeValueAsString(update);
            log.info("收到更新 json={}", json);
        } catch (JsonProcessingException e) {
            log.warn("無法序列化 Update 成 JSON", e);
        }
    }

    /**
     * 複製單一訊息到公開頻道，並移除 caption。
     *
     * @param channelPost 來源訊息
     * @param sourceChannelId 來源頻道 ID
     */
    private void copySingleMessage(Message channelPost, String sourceChannelId) {
        Bot mainBotEntity = botService.findByBotType(BotType.MAIN);
        CopyMessages copyMessages = CopyMessages.builder()
                .chatId(appConfig.getBotPublicChannelId())
                .fromChatId(appConfig.getBotCommunicateChannelChatId())
                .messageIds(List.of(channelPost.getMessageId()))
                .removeCaption(true)
                .build();
        telegramBotClient.send(copyMessages, mainBotEntity);
        log.info("已複製訊息 {} 從 {} 到 {}", channelPost.getMessageId(), sourceChannelId, appConfig.getBotPublicChannelId());
    }

    /**
     * 將同一個 media group 的訊息 ID 暫存，並延後 2 秒批次複製。
     * 複製時使用 CopyMessages 以保留相簿格式並移除 caption。
     *
     * @param channelPost 來源訊息
     */
    private void bufferMediaGroupCopy(Message channelPost) {
        String mediaGroupId = channelPost.getMediaGroupId();
        MediaGroupBuffer buffer = mediaGroupBuffers.computeIfAbsent(mediaGroupId, key -> new MediaGroupBuffer());
        synchronized (buffer) {
            buffer.messageIds.add(channelPost.getMessageId());
            if (buffer.flushFuture != null) {
                buffer.flushFuture.cancel(false);
            }
            buffer.flushFuture = mediaGroupScheduler.schedule(() -> flushMediaGroupCopy(mediaGroupId), MEDIA_GROUP_FLUSH_DELAY_MS, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * 將緩衝完成的 media group 一次複製到公開頻道。
     *
     * @param mediaGroupId media group ID
     */
    private void flushMediaGroupCopy(String mediaGroupId) {
        MediaGroupBuffer buffer = mediaGroupBuffers.remove(mediaGroupId);
        if (buffer == null) {
            return;
        }
        List<Integer> messageIds;
        synchronized (buffer) {
            if (buffer.messageIds.isEmpty()) {
                return;
            }
            messageIds = new ArrayList<>(buffer.messageIds);
        }

        Bot mainBotEntity = botService.findByBotType(BotType.MAIN);
        CopyMessages copyMessages = CopyMessages.builder()
                .chatId(appConfig.getBotPublicChannelId())
                .fromChatId(appConfig.getBotCommunicateChannelChatId())
                .messageIds(messageIds)
                .removeCaption(true)
                .build();
        telegramBotClient.send(copyMessages, mainBotEntity);
        log.info("已複製 media group {}，共 {} 則訊息", mediaGroupId, messageIds.size());
    }

    /**
     * 判斷是否為純文字訊息（不含任何媒體）。
     *
     * @param message Telegram 訊息
     * @return true 表示純文字
     */
    private boolean isPureText(Message message) {
        if (message.getText() == null) {
            return false;
        }
        return !(message.hasPhoto()
                || message.hasVideo()
                || message.hasDocument()
                || message.hasAudio()
                || message.hasAnimation()
                || message.hasVoice()
                || message.hasVideoNote()
                || message.hasSticker());
    }

    private static class MediaGroupBuffer {
        private final List<Integer> messageIds = new ArrayList<>();
        private ScheduledFuture<?> flushFuture;
    }
}
