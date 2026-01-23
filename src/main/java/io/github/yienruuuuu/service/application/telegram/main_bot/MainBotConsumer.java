package io.github.yienruuuuu.service.application.telegram.main_bot;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.yienruuuuu.bean.entity.Bot;
import io.github.yienruuuuu.bean.enums.BotType;
import io.github.yienruuuuu.config.AppConfig;
import io.github.yienruuuuu.service.application.telegram.TelegramBotClient;
import io.github.yienruuuuu.service.business.BotService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.send.SendAnimation;
import org.telegram.telegrambots.meta.api.methods.send.SendAudio;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMediaGroup;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.send.SendVideo;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.PhotoSize;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.media.InputMedia;
import org.telegram.telegrambots.meta.api.objects.media.InputMediaAnimation;
import org.telegram.telegrambots.meta.api.objects.media.InputMediaAudio;
import org.telegram.telegrambots.meta.api.objects.media.InputMediaDocument;
import org.telegram.telegrambots.meta.api.objects.media.InputMediaPhoto;
import org.telegram.telegrambots.meta.api.objects.media.InputMediaVideo;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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
    private static final DateTimeFormatter SERIAL_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private final BotService botService;
    private final AppConfig appConfig;
    private final TelegramBotClient telegramBotClient;
    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService mediaGroupScheduler = Executors.newSingleThreadScheduledExecutor();
    private final Map<String, MediaGroupBuffer> mediaGroupBuffers = new ConcurrentHashMap<>();
    private final Object serialLock = new Object();
    private LocalDate currentSerialDate = LocalDate.now();
    private int currentSerial = 0;

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
     * 處理 Telegram 更新：記錄 JSON、過濾來源頻道，並以自訂序號轉送媒體。
     *
     * @param update Telegram 更新
     */
    @Override
    public void consume(Update update) {
        logIncomingUpdateJson(update);
        if (!update.hasChannelPost()) {
            return;
        }

        Message channelPost = update.getChannelPost();
        String sourceChannelId = String.valueOf(channelPost.getChatId());
        if (!sourceChannelId.equals(appConfig.getBotCommunicateChannelChatId())) {
            return;
        }

        if (isPureText(channelPost)) {
            log.info("略過純文字訊息 {}", channelPost.getMessageId());
            return;
        }

        if (channelPost.getMediaGroupId() != null) {
            bufferMediaGroupCopy(channelPost);
            return;
        }

        sendSingleMediaMessage(channelPost, sourceChannelId);
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
     * 以原檔案組出單一媒體訊息，並以序號作為 caption 發送到公開頻道。
     *
     * @param channelPost 來源訊息
     * @param sourceChannelId 來源頻道 ID
     */
    private void sendSingleMediaMessage(Message channelPost, String sourceChannelId) {
        String serial = nextSerial();
        Bot mainBotEntity = botService.findByBotType(BotType.MAIN);
        boolean sent = sendSingleMedia(channelPost, serial, mainBotEntity);
        if (!sent) {
            log.warn("不支援的媒體型別，略過訊息 {}", channelPost.getMessageId());
            return;
        }
        sendAcknowledgement(serial, channelPost.getMessageId(), mainBotEntity);
        log.info("已發送序號 {} 對應來源 {}，送達 {}", serial, sourceChannelId, appConfig.getBotPublicChannelId());
    }

    /**
     * 將同一個 media group 的訊息暫存，並延後 2 秒批次發送。
     * 發送時以序號為 caption，且不帶來源文字。
     *
     * @param channelPost 來源訊息
     */
    private void bufferMediaGroupCopy(Message channelPost) {
        String mediaGroupId = channelPost.getMediaGroupId();
        MediaGroupBuffer buffer = mediaGroupBuffers.computeIfAbsent(mediaGroupId, key -> new MediaGroupBuffer());
        synchronized (buffer) {
            buffer.messages.add(channelPost);
            if (buffer.flushFuture != null) {
                buffer.flushFuture.cancel(false);
            }
            buffer.flushFuture = mediaGroupScheduler.schedule(() -> flushMediaGroupCopy(mediaGroupId), MEDIA_GROUP_FLUSH_DELAY_MS, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * 將緩衝完成的 media group 一次發送到公開頻道。
     *
     * @param mediaGroupId media group ID
     */
    private void flushMediaGroupCopy(String mediaGroupId) {
        MediaGroupBuffer buffer = mediaGroupBuffers.remove(mediaGroupId);
        if (buffer == null) {
            return;
        }
        List<Message> messages;
        synchronized (buffer) {
            if (buffer.messages.isEmpty()) {
                return;
            }
            messages = new ArrayList<>(buffer.messages);
        }

        String serial = nextSerial();
        Bot mainBotEntity = botService.findByBotType(BotType.MAIN);
        List<InputMedia> medias = buildMediaGroupMedias(messages, serial);
        if (medias.isEmpty()) {
            log.warn("media group {} 無可用媒體，略過發送", mediaGroupId);
            return;
        }
        SendMediaGroup sendMediaGroup = SendMediaGroup.builder()
                .chatId(appConfig.getBotPublicChannelId())
                .medias(medias)
                .build();
        telegramBotClient.send(sendMediaGroup, mainBotEntity);
        sendAcknowledgement(serial, messages.get(0).getMessageId(), mainBotEntity);
        log.info("已發送 media group {} 序號 {}，共 {} 則訊息", mediaGroupId, serial, medias.size());
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
        private final List<Message> messages = new ArrayList<>();
        private ScheduledFuture<?> flushFuture;
    }

    /**
     * 產生每日遞增的序號字串，例如 2026-01-23_0001。
     *
     * @return 序號字串
     */
    private String nextSerial() {
        synchronized (serialLock) {
            LocalDate today = LocalDate.now();
            if (!today.equals(currentSerialDate)) {
                currentSerialDate = today;
                currentSerial = 0;
            }
            int next = ++currentSerial;
            return SERIAL_DATE_FORMAT.format(today) + "_" + String.format("%04d", next);
        }
    }

    /**
     * 依訊息類型發送單一媒體，caption 使用序號。
     *
     * @param message 來源訊息
     * @param serial  序號文字
     * @param bot     Bot 實體
     * @return 是否成功送出
     */
    private boolean sendSingleMedia(Message message, String serial, Bot bot) {
        if (message.hasPhoto()) {
            List<PhotoSize> photos = message.getPhoto();
            if (photos == null || photos.isEmpty()) {
                return false;
            }
            String fileId = photos.get(photos.size() - 1).getFileId();
            SendPhoto sendPhoto = SendPhoto.builder()
                    .chatId(appConfig.getBotPublicChannelId())
                    .photo(new InputFile(fileId))
                    .caption(serial)
                    .build();
            telegramBotClient.send(sendPhoto, bot);
            return true;
        }
        if (message.hasVideo()) {
            String fileId = message.getVideo().getFileId();
            SendVideo sendVideo = SendVideo.builder()
                    .chatId(appConfig.getBotPublicChannelId())
                    .video(new InputFile(fileId))
                    .caption(serial)
                    .build();
            telegramBotClient.send(sendVideo, bot);
            return true;
        }
        if (message.hasDocument()) {
            String fileId = message.getDocument().getFileId();
            SendDocument sendDocument = SendDocument.builder()
                    .chatId(appConfig.getBotPublicChannelId())
                    .document(new InputFile(fileId))
                    .caption(serial)
                    .build();
            telegramBotClient.send(sendDocument, bot);
            return true;
        }
        if (message.hasAudio()) {
            String fileId = message.getAudio().getFileId();
            SendAudio sendAudio = SendAudio.builder()
                    .chatId(appConfig.getBotPublicChannelId())
                    .audio(new InputFile(fileId))
                    .caption(serial)
                    .build();
            telegramBotClient.send(sendAudio, bot);
            return true;
        }
        if (message.hasAnimation()) {
            String fileId = message.getAnimation().getFileId();
            SendAnimation sendAnimation = SendAnimation.builder()
                    .chatId(appConfig.getBotPublicChannelId())
                    .animation(new InputFile(fileId))
                    .caption(serial)
                    .build();
            telegramBotClient.send(sendAnimation, bot);
            return true;
        }
        return false;
    }

    /**
     * 將一組訊息轉成 media group，第一則套用序號 caption。
     *
     * @param messages 來源訊息列表
     * @param serial   序號文字
     * @return InputMedia 列表
     */
    private List<InputMedia> buildMediaGroupMedias(List<Message> messages, String serial) {
        List<InputMedia> medias = new ArrayList<>();
        boolean captionApplied = false;
        for (Message message : messages) {
            InputMedia media = buildInputMedia(message);
            if (media == null) {
                log.warn("media group 內含不支援媒體，messageId={}", message.getMessageId());
                continue;
            }
            if (!captionApplied) {
                media.setCaption(serial);
                captionApplied = true;
            }
            medias.add(media);
        }
        return medias;
    }

    /**
     * 由單一訊息建立對應的 InputMedia。
     *
     * @param message 來源訊息
     * @return InputMedia，若不支援則回傳 null
     */
    private InputMedia buildInputMedia(Message message) {
        if (message.hasPhoto()) {
            List<PhotoSize> photos = message.getPhoto();
            if (photos == null || photos.isEmpty()) {
                return null;
            }
            String fileId = photos.get(photos.size() - 1).getFileId();
            return new InputMediaPhoto(fileId);
        }
        if (message.hasVideo()) {
            return new InputMediaVideo(message.getVideo().getFileId());
        }
        if (message.hasDocument()) {
            return new InputMediaDocument(message.getDocument().getFileId());
        }
        if (message.hasAudio()) {
            return new InputMediaAudio(message.getAudio().getFileId());
        }
        if (message.hasAnimation()) {
            return new InputMediaAnimation(message.getAnimation().getFileId());
        }
        return null;
    }

    /**
     * 在來源群組回覆序號，方便對應兩邊資源。
     *
     * @param serial 序號文字
     * @param replyToMessageId 回覆的訊息 ID
     * @param bot Bot 實體
     */
    private void sendAcknowledgement(String serial, Integer replyToMessageId, Bot bot) {
        SendMessage sendMessage = SendMessage.builder()
                .chatId(appConfig.getBotCommunicateChannelChatId())
                .text(serial)
                .replyToMessageId(replyToMessageId)
                .build();
        telegramBotClient.send(sendMessage, bot);
    }
}
