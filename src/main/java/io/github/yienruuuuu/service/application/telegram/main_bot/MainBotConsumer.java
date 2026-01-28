package io.github.yienruuuuu.service.application.telegram.main_bot;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.houbb.opencc4j.util.ZhConverterUtil;
import io.github.yienruuuuu.bean.entity.Bot;
import io.github.yienruuuuu.bean.entity.ForwardPost;
import io.github.yienruuuuu.bean.entity.ForwardPostMedia;
import io.github.yienruuuuu.bean.enums.BotType;
import io.github.yienruuuuu.config.AppConfig;
import io.github.yienruuuuu.service.application.telegram.TelegramBotClient;
import io.github.yienruuuuu.service.business.ChannelSuffixService;
import io.github.yienruuuuu.service.business.BlacklistService;
import io.github.yienruuuuu.service.business.BotService;
import io.github.yienruuuuu.service.business.ForwardPostService;
import io.github.yienruuuuu.service.business.PromoContentService;
import io.github.yienruuuuu.service.business.model.ForwardPostMediaItem;
import jakarta.annotation.PostConstruct;
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
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
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
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
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
    private static final String CALLBACK_RESEND_ALL = "resend_all";
    private final BotService botService;
    private final BlacklistService blacklistService;
    private final ForwardPostService forwardPostService;
    private final ChannelSuffixService channelSuffixService;
    private final PromoContentService promoContentService;
    private final AppConfig appConfig;
    private final TelegramBotClient telegramBotClient;
    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService mediaGroupScheduler = Executors.newSingleThreadScheduledExecutor();
    private final ScheduledExecutorService resendScheduler = Executors.newSingleThreadScheduledExecutor();
    private final Map<String, MediaGroupBuffer> mediaGroupBuffers = new ConcurrentHashMap<>();
    private final AtomicBoolean resendRunning = new AtomicBoolean(false);
    private final Object serialLock = new Object();
    private LocalDate currentSerialDate = LocalDate.now();
    private int currentSerial = 0;

    /**
     * 建立主要更新消費者，注入必要的服務與工具。
     *
     * @param botService Bot 服務
     * @param blacklistService 黑名單服務
     * @param forwardPostService 貼文記錄服務
     * @param appConfig  應用設定
     * @param telegramBotClient Telegram API 呼叫封裝
     * @param objectMapper JSON 序列化工具
     */
    @Autowired
    public MainBotConsumer(
            BotService botService,
            BlacklistService blacklistService,
            ForwardPostService forwardPostService,
            ChannelSuffixService channelSuffixService,
            PromoContentService promoContentService,
            AppConfig appConfig,
            TelegramBotClient telegramBotClient,
            ObjectMapper objectMapper
    ) {
        this.botService = botService;
        this.blacklistService = blacklistService;
        this.forwardPostService = forwardPostService;
        this.channelSuffixService = channelSuffixService;
        this.promoContentService = promoContentService;
        this.appConfig = appConfig;
        this.telegramBotClient = telegramBotClient;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    private void initSerialCounter() {
        synchronized (serialLock) {
            LocalDate today = LocalDate.now();
            String prefix = SERIAL_DATE_FORMAT.format(today) + "_";
            String latestSerial = forwardPostService.findLatestSerialByPrefix(prefix);
            currentSerialDate = today;
            currentSerial = parseSerialIndex(latestSerial);
            if (currentSerial > 0) {
                log.info("序號初始化完成，最後序號 {}", latestSerial);
            }
        }
    }

    /**
     * 處理 Telegram 更新：記錄 JSON、處理回呼、過濾來源頻道並轉送內容。
     *
     * @param update Telegram 更新
     */
    @Override
    public void consume(Update update) {
        logIncomingUpdateJson(update);
        if (update.hasCallbackQuery()) {
            handleCallbackQuery(update);
        }
        if (update.hasMessage()) {
            handleAdminMessage(update.getMessage());
        }
        if (!update.hasChannelPost()) {
            return;
        }

        Message channelPost = update.getChannelPost();
        String sourceChannelId = String.valueOf(channelPost.getChatId());
        if (!sourceChannelId.equals(appConfig.getBotCommunicateChannelChatId())) {
            return;
        }

        if (isPureText(channelPost)) {
            sendTextMessage(channelPost, sourceChannelId);
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
            log.debug("收到更新 json={}", json);
        } catch (JsonProcessingException e) {
            log.warn("無法序列化 Update 成 JSON", e);
        }
    }

    /**
     * 轉送純文字訊息到公開群組，並附加序號與宣傳文字。
     *
     * @param channelPost 來源訊息
     * @param sourceChannelId 來源頻道 ID
     */
    private void sendTextMessage(Message channelPost, String sourceChannelId) {
        String serial = nextSerial();
        String originalText = channelPost.getText();
        String processedText = processText(originalText);
        String forwardFromChatId = channelPost.getForwardFromChat() == null
                ? null
                : String.valueOf(channelPost.getForwardFromChat().getId());
        String forwardFromChatTitle = channelPost.getForwardFromChat() == null
                ? null
                : channelPost.getForwardFromChat().getTitle();
        String promoText = promoContentService.pickRandomContent();
        String suffixText = channelSuffixService.pickSuffixByForwardFromChatId(forwardFromChatId);
        String outputText = buildOutputText(processedText, serial, promoText, suffixText);
        Bot mainBotEntity = botService.findByBotType(BotType.MAIN);

        SendMessage sendMessage = SendMessage.builder()
                .chatId(appConfig.getBotPublicChannelId())
                .text(outputText)
                .build();
        telegramBotClient.send(sendMessage, mainBotEntity);

        ForwardPost post = forwardPostService.createPost(
                serial,
                sourceChannelId,
                channelPost.getMessageId(),
                channelPost.getMediaGroupId(),
                forwardFromChatId,
                forwardFromChatTitle,
                originalText,
                processedText,
                outputText,
                List.of()
        );
        sendAcknowledgement(serial, post.getId(), channelPost.getMessageId(), mainBotEntity);
        log.info("已發送文字序號 {} 對應來源 {}，送達 {}", serial, sourceChannelId, appConfig.getBotPublicChannelId());
    }

    /**
     * 以原檔案組出單一媒體訊息，並以處理後文字加上序號與宣傳文字作為 caption。
     *
     * @param channelPost 來源訊息
     * @param sourceChannelId 來源頻道 ID
     */
    private void sendSingleMediaMessage(Message channelPost, String sourceChannelId) {
        if (isDuplicateMediaMessage(channelPost)) {
            sendDuplicateNotice(channelPost.getMessageId());
            return;
        }
        String serial = nextSerial();
        String originalText = channelPost.getCaption();
        String processedText = processText(originalText);
        String forwardFromChatId = channelPost.getForwardFromChat() == null
                ? null
                : String.valueOf(channelPost.getForwardFromChat().getId());
        String forwardFromChatTitle = channelPost.getForwardFromChat() == null
                ? null
                : channelPost.getForwardFromChat().getTitle();
        String promoText = promoContentService.pickRandomContent();
        String suffixText = channelSuffixService.pickSuffixByForwardFromChatId(forwardFromChatId);
        String outputText = buildOutputText(processedText, serial, promoText, suffixText);
        Bot mainBotEntity = botService.findByBotType(BotType.MAIN);
        boolean sent = sendSingleMedia(channelPost, outputText, mainBotEntity);
        if (!sent) {
            log.warn("不支援的媒體型別，略過訊息 {}", channelPost.getMessageId());
            return;
        }

        ForwardPost post = forwardPostService.createPost(
                serial,
                sourceChannelId,
                channelPost.getMessageId(),
                channelPost.getMediaGroupId(),
                forwardFromChatId,
                forwardFromChatTitle,
                originalText,
                processedText,
                outputText,
                buildMediaItemsFromMessage(channelPost)
        );
        sendAcknowledgement(serial, post.getId(), channelPost.getMessageId(), mainBotEntity);
        log.info("已發送序號 {} 對應來源 {}，送達 {}", serial, sourceChannelId, appConfig.getBotPublicChannelId());
    }

    /**
     * 將同一個 media group 的訊息暫存，並延後 2 秒批次發送。
     * 發送時以處理後文字、序號與宣傳文字為 caption。
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
        if (isDuplicateMediaGroup(messages)) {
            sendDuplicateNotice(messages.get(0).getMessageId());
            return;
        }

        String serial = nextSerial();
        String originalText = extractMediaGroupText(messages);
        String processedText = processText(originalText);
        Message firstMessage = messages.get(0);
        String forwardFromChatId = firstMessage.getForwardFromChat() == null
                ? null
                : String.valueOf(firstMessage.getForwardFromChat().getId());
        String forwardFromChatTitle = firstMessage.getForwardFromChat() == null
                ? null
                : firstMessage.getForwardFromChat().getTitle();
        String promoText = promoContentService.pickRandomContent();
        String suffixText = channelSuffixService.pickSuffixByForwardFromChatId(forwardFromChatId);
        String outputText = buildOutputText(processedText, serial, promoText, suffixText);
        Bot mainBotEntity = botService.findByBotType(BotType.MAIN);
        List<InputMedia> medias = buildMediaGroupMedias(messages, outputText);
        if (medias.isEmpty()) {
            log.warn("media group {} 無可用媒體，略過發送", mediaGroupId);
            return;
        }
        SendMediaGroup sendMediaGroup = SendMediaGroup.builder()
                .chatId(appConfig.getBotPublicChannelId())
                .medias(medias)
                .build();
        telegramBotClient.send(sendMediaGroup, mainBotEntity);

        ForwardPost post = forwardPostService.createPost(
                serial,
                String.valueOf(messages.get(0).getChatId()),
                messages.get(0).getMessageId(),
                mediaGroupId,
                forwardFromChatId,
                forwardFromChatTitle,
                originalText,
                processedText,
                outputText,
                buildMediaItemsFromMessages(messages)
        );
        sendAcknowledgement(serial, post.getId(), messages.get(0).getMessageId(), mainBotEntity);
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

    private int parseSerialIndex(String serial) {
        if (serial == null || serial.isBlank()) {
            return 0;
        }
        int underscoreIndex = serial.lastIndexOf('_');
        if (underscoreIndex < 0 || underscoreIndex == serial.length() - 1) {
            return 0;
        }
        String number = serial.substring(underscoreIndex + 1);
        try {
            return Integer.parseInt(number);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 依訊息類型發送單一媒體，caption 使用處理後文字。
     *
     * @param message 來源訊息
     * @param caption  處理後文字
     * @param bot     Bot 實體
     * @return 是否成功送出
     */
    private boolean sendSingleMedia(Message message, String caption, Bot bot) {
        if (message.hasPhoto()) {
            List<PhotoSize> photos = message.getPhoto();
            if (photos == null || photos.isEmpty()) {
                return false;
            }
            String fileId = photos.get(photos.size() - 1).getFileId();
            SendPhoto sendPhoto = SendPhoto.builder()
                    .chatId(appConfig.getBotPublicChannelId())
                    .photo(new InputFile(fileId))
                    .caption(caption)
                    .build();
            telegramBotClient.send(sendPhoto, bot);
            return true;
        }
        if (message.hasVideo()) {
            String fileId = message.getVideo().getFileId();
            SendVideo sendVideo = SendVideo.builder()
                    .chatId(appConfig.getBotPublicChannelId())
                    .video(new InputFile(fileId))
                    .caption(caption)
                    .build();
            telegramBotClient.send(sendVideo, bot);
            return true;
        }
        if (message.hasDocument()) {
            String fileId = message.getDocument().getFileId();
            SendDocument sendDocument = SendDocument.builder()
                    .chatId(appConfig.getBotPublicChannelId())
                    .document(new InputFile(fileId))
                    .caption(caption)
                    .build();
            telegramBotClient.send(sendDocument, bot);
            return true;
        }
        if (message.hasAudio()) {
            String fileId = message.getAudio().getFileId();
            SendAudio sendAudio = SendAudio.builder()
                    .chatId(appConfig.getBotPublicChannelId())
                    .audio(new InputFile(fileId))
                    .caption(caption)
                    .build();
            telegramBotClient.send(sendAudio, bot);
            return true;
        }
        if (message.hasAnimation()) {
            String fileId = message.getAnimation().getFileId();
            SendAnimation sendAnimation = SendAnimation.builder()
                    .chatId(appConfig.getBotPublicChannelId())
                    .animation(new InputFile(fileId))
                    .caption(caption)
                    .build();
            telegramBotClient.send(sendAnimation, bot);
            return true;
        }
        return false;
    }

    /**
     * 依媒體類型發送單一媒體，用於重送。
     *
     * @param mediaType 媒體類型
     * @param fileId 檔案 ID
     * @param caption 文字內容
     * @param bot Bot 實體
     * @return 是否成功送出
     */
    private boolean sendSingleMediaByType(String mediaType, String fileId, String caption, Bot bot, String chatId) {
        if ("photo".equals(mediaType)) {
            SendPhoto sendPhoto = SendPhoto.builder()
                    .chatId(chatId)
                    .photo(new InputFile(fileId))
                    .caption(caption)
                    .build();
            telegramBotClient.send(sendPhoto, bot);
            return true;
        }
        if ("video".equals(mediaType)) {
            SendVideo sendVideo = SendVideo.builder()
                    .chatId(chatId)
                    .video(new InputFile(fileId))
                    .caption(caption)
                    .build();
            telegramBotClient.send(sendVideo, bot);
            return true;
        }
        if ("document".equals(mediaType)) {
            SendDocument sendDocument = SendDocument.builder()
                    .chatId(chatId)
                    .document(new InputFile(fileId))
                    .caption(caption)
                    .build();
            telegramBotClient.send(sendDocument, bot);
            return true;
        }
        if ("audio".equals(mediaType)) {
            SendAudio sendAudio = SendAudio.builder()
                    .chatId(chatId)
                    .audio(new InputFile(fileId))
                    .caption(caption)
                    .build();
            telegramBotClient.send(sendAudio, bot);
            return true;
        }
        if ("animation".equals(mediaType)) {
            SendAnimation sendAnimation = SendAnimation.builder()
                    .chatId(chatId)
                    .animation(new InputFile(fileId))
                    .caption(caption)
                    .build();
            telegramBotClient.send(sendAnimation, bot);
            return true;
        }
        return false;
    }

    /**
     * 將一組訊息轉成 media group，第一則套用處理後 caption。
     *
     * @param messages 來源訊息列表
     * @param caption   處理後文字
     * @return InputMedia 列表
     */
    private List<InputMedia> buildMediaGroupMedias(List<Message> messages, String caption) {
        List<InputMedia> medias = new ArrayList<>();
        boolean captionApplied = false;
        for (Message message : messages) {
            InputMedia media = buildInputMedia(message);
            if (media == null) {
                log.warn("media group 內含不支援媒體，messageId={}", message.getMessageId());
                continue;
            }
            if (!captionApplied) {
                media.setCaption(caption);
                captionApplied = true;
            }
            medias.add(media);
        }
        return medias;
    }

    /**
     * 由媒體記錄建立 media group 的 InputMedia。
     *
     * @param medias 媒體記錄列表
     * @param caption 第一筆 caption
     * @return InputMedia 列表
     */
    private List<InputMedia> buildMediaGroupMediasFromRecords(List<ForwardPostMedia> medias, String caption) {
        List<InputMedia> inputMedias = new ArrayList<>();
        boolean captionApplied = false;
        for (ForwardPostMedia media : medias) {
            InputMedia inputMedia = buildInputMediaByType(media.getMediaType(), media.getFileId());
            if (inputMedia == null) {
                continue;
            }
            if (!captionApplied) {
                inputMedia.setCaption(caption);
                captionApplied = true;
            }
            inputMedias.add(inputMedia);
        }
        return inputMedias;
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
     * 依媒體類型建立 InputMedia。
     *
     * @param mediaType 媒體類型
     * @param fileId 檔案 ID
     * @return InputMedia 或 null
     */
    private InputMedia buildInputMediaByType(String mediaType, String fileId) {
        if ("photo".equals(mediaType)) {
            return new InputMediaPhoto(fileId);
        }
        if ("video".equals(mediaType)) {
            return new InputMediaVideo(fileId);
        }
        if ("document".equals(mediaType)) {
            return new InputMediaDocument(fileId);
        }
        if ("audio".equals(mediaType)) {
            return new InputMediaAudio(fileId);
        }
        if ("animation".equals(mediaType)) {
            return new InputMediaAnimation(fileId);
        }
        return null;
    }

    /**
     * 從 media group 中擷取第一筆文字或 caption。
     *
     * @param messages 來源訊息列表
     * @return 文字內容或 null
     */
    private String extractMediaGroupText(List<Message> messages) {
        for (Message message : messages) {
            if (message.getCaption() != null && !message.getCaption().isBlank()) {
                return message.getCaption();
            }
            if (message.getText() != null && !message.getText().isBlank()) {
                return message.getText();
            }
        }
        return null;
    }

    /**
     * 先移除黑名單字串，再進行簡轉繁。
     *
     * @param input 原始文字
     * @return 處理後文字
     */
    private String processText(String input) {
        String filtered = blacklistService.filter(input);
        String converted = filtered == null ? null : ZhConverterUtil.toTraditional(filtered);
        return converted == null ? "" : converted.trim();
    }

    /**
     * 將處理後文字附加序號與宣傳文字。
     *
     * @param processedText 處理後文字
     * @param serial 序號
     * @return 最終輸出文字
     */
    private String buildOutputText(String processedText, String serial, String promoText, String suffixText) {
        List<String> parts = new ArrayList<>();
        if (suffixText != null && !suffixText.isBlank()) {
            parts.add(suffixText);
        }
        if (processedText != null && !processedText.isBlank()) {
            parts.add(processedText);
        }
        if (serial != null && !serial.isBlank()) {
            parts.add(serial);
        }
        if (promoText != null && !promoText.isBlank()) {
            parts.add(promoText);
        }
        return String.join(" ", parts);
    }

    /**
     * 建立媒體項目列表（單一訊息）。
     *
     * @param message 來源訊息
     * @return 媒體項目列表
     */
    private List<ForwardPostMediaItem> buildMediaItemsFromMessage(Message message) {
        List<ForwardPostMediaItem> items = new ArrayList<>();
        ForwardPostMediaItem item = buildMediaItem(message);
        if (item != null) {
            items.add(item);
        }
        return items;
    }

    /**
     * 建立媒體項目列表（多訊息）。
     *
     * @param messages 來源訊息列表
     * @return 媒體項目列表
     */
    private List<ForwardPostMediaItem> buildMediaItemsFromMessages(List<Message> messages) {
        List<ForwardPostMediaItem> items = new ArrayList<>();
        for (Message message : messages) {
            ForwardPostMediaItem item = buildMediaItem(message);
            if (item != null) {
                items.add(item);
            }
        }
        return items;
    }

    /**
     * 由訊息建立媒體項目。
     *
     * @param message 來源訊息
     * @return 媒體項目或 null
     */
    private ForwardPostMediaItem buildMediaItem(Message message) {
        if (message.hasPhoto()) {
            List<PhotoSize> photos = message.getPhoto();
            if (photos == null || photos.isEmpty()) {
                return null;
            }
            String fileId = photos.get(photos.size() - 1).getFileId();
            return new ForwardPostMediaItem("photo", fileId);
        }
        if (message.hasVideo()) {
            return new ForwardPostMediaItem("video", message.getVideo().getFileId());
        }
        if (message.hasDocument()) {
            return new ForwardPostMediaItem("document", message.getDocument().getFileId());
        }
        if (message.hasAudio()) {
            return new ForwardPostMediaItem("audio", message.getAudio().getFileId());
        }
        if (message.hasAnimation()) {
            return new ForwardPostMediaItem("animation", message.getAnimation().getFileId());
        }
        return null;
    }

    private boolean isDuplicateMediaMessage(Message message) {
        ForwardPostMediaItem item = buildMediaItem(message);
        if (item == null || item.getFileId() == null || item.getFileId().isBlank()) {
            return false;
        }
        return forwardPostService.existsByMediaFileId(item.getFileId());
    }

    private boolean isDuplicateMediaGroup(List<Message> messages) {
        List<ForwardPostMediaItem> items = buildMediaItemsFromMessages(messages);
        for (ForwardPostMediaItem item : items) {
            if (item.getFileId() == null || item.getFileId().isBlank()) {
                continue;
            }
            if (forwardPostService.existsByMediaFileId(item.getFileId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 在來源群組回覆序號，並附上重送按鈕。
     *
     * @param serial 序號文字
     * @param postId 貼文 ID
     * @param replyToMessageId 回覆的訊息 ID
     * @param bot Bot 實體
     */
    private void sendAcknowledgement(String serial, String postId, Integer replyToMessageId, Bot bot) {
        SendMessage sendMessage = SendMessage.builder()
                .chatId(appConfig.getBotCommunicateChannelChatId())
                .text(serial)
                .replyToMessageId(replyToMessageId)
                .build();
        telegramBotClient.send(sendMessage, bot);
    }

    private void sendDuplicateNotice(Integer replyToMessageId) {
        Bot mainBotEntity = botService.findByBotType(BotType.MAIN);
        SendMessage sendMessage = SendMessage.builder()
                .chatId(appConfig.getBotCommunicateChannelChatId())
                .text("重複轉傳")
                .replyToMessageId(replyToMessageId)
                .build();
        telegramBotClient.send(sendMessage, mainBotEntity);
    }

    /**
     * 處理重送回呼，只允許管理員操作。
     *
     * @param update Telegram 更新
     */
    private void handleCallbackQuery(Update update) {
        String adminId = appConfig.getBotAdminId();
        if (adminId == null || adminId.isBlank()) {
            log.warn("未設定 admin id，忽略回呼");
            return;
        }
        if (update.getCallbackQuery() == null || update.getCallbackQuery().getFrom() == null) {
            return;
        }
        answerCallback(update.getCallbackQuery().getId(), "已收到指令");
        String fromId = String.valueOf(update.getCallbackQuery().getFrom().getId());
        if (!adminId.equals(fromId)) {
            log.warn("非管理員觸發回呼，fromId={}", fromId);
            return;
        }

        String data = update.getCallbackQuery().getData();
        if (data == null || !CALLBACK_RESEND_ALL.equals(data)) {
            return;
        }
        Integer statusMessageId = null;
        if (update.getCallbackQuery().getMessage() != null) {
            String chatId = String.valueOf(update.getCallbackQuery().getMessage().getChatId());
            statusMessageId = update.getCallbackQuery().getMessage().getMessageId();
            EditMessageText editText = EditMessageText.builder()
                    .chatId(chatId)
                    .messageId(statusMessageId)
                    .text("重送中")
                    .build();
            telegramBotClient.send(editText, botService.findByBotType(BotType.MAIN));
            if (update.getCallbackQuery().getMessage() instanceof Message callbackMessage
                    && callbackMessage.getReplyMarkup() != null) {
                EditMessageReplyMarkup clearMarkup = EditMessageReplyMarkup.builder()
                        .chatId(chatId)
                        .messageId(statusMessageId)
                        .build();
                telegramBotClient.send(clearMarkup, botService.findByBotType(BotType.MAIN));
            }
        }
        String chatId = update.getCallbackQuery().getMessage() == null
                ? String.valueOf(update.getCallbackQuery().getFrom().getId())
                : String.valueOf(update.getCallbackQuery().getMessage().getChatId());
        startResendAll(chatId, statusMessageId);
    }

    /**
     * 回覆 callback query，避免按鈕持續顯示等待狀態。
     *
     * @param callbackQueryId 回呼 ID
     * @param text 回覆文字
     */
    private void answerCallback(String callbackQueryId, String text) {
        Bot mainBotEntity = botService.findByBotType(BotType.MAIN);
        AnswerCallbackQuery answer = AnswerCallbackQuery.builder()
                .callbackQueryId(callbackQueryId)
                .text(text)
                .build();
        telegramBotClient.send(answer, mainBotEntity);
    }

    /**
     * 處理管理員私訊指令，觸發重送按鈕。
     *
     * @param message 來源訊息
     */
    private void handleAdminMessage(Message message) {
        if (message.getChat() == null || !"private".equals(message.getChat().getType())) {
            return;
        }
        String adminId = appConfig.getBotAdminId();
        if (adminId == null || adminId.isBlank()) {
            return;
        }
        if (message.getFrom() == null || !adminId.equals(String.valueOf(message.getFrom().getId()))) {
            return;
        }
        if (message.getText() == null) {
            return;
        }
        String text = message.getText().trim();
        if (!"/resend".equalsIgnoreCase(text) && !"重送".equals(text)) {
            return;
        }
        InlineKeyboardButton resendButton = InlineKeyboardButton.builder()
                .text("開始重送")
                .callbackData(CALLBACK_RESEND_ALL)
                .build();
        InlineKeyboardRow row = new InlineKeyboardRow(List.of(resendButton));
        InlineKeyboardMarkup markup = InlineKeyboardMarkup.builder()
                .keyboard(List.of(row))
                .build();
        SendMessage prompt = SendMessage.builder()
                .chatId(String.valueOf(message.getChatId()))
                .text("確認開始重送全部歷史紀錄？")
                .replyMarkup(markup)
                .build();
        Bot mainBotEntity = botService.findByBotType(BotType.MAIN);
        telegramBotClient.send(prompt, mainBotEntity);
    }

    /**
     * 啟動歷史貼文重送流程，依固定間隔送往 B。
     *
     * @param notifyChatId 通知聊天 ID
     */
    private void startResendAll(String notifyChatId, Integer statusMessageId) {
        if (!resendRunning.compareAndSet(false, true)) {
            updateStatusMessage(notifyChatId, statusMessageId, "重送中，請稍後再試");
            return;
        }
        List<ForwardPost> posts = forwardPostService.findAllOrderByCreatedAtAsc();
        if (posts.isEmpty()) {
            resendRunning.set(false);
            updateStatusMessage(notifyChatId, statusMessageId, "沒有可重送的歷史紀錄");
            return;
        }
        Bot mainBotEntity = botService.findByBotType(BotType.MAIN);
        int total = posts.size();
        int progressStep = Math.max(1, total / 20);
        AtomicInteger index = new AtomicInteger(0);
        if (statusMessageId != null) {
            updateStatusMessage(notifyChatId, statusMessageId, buildProgressText(0, total));
        }
        AtomicReference<ScheduledFuture<?>> taskRef = new AtomicReference<>();
        ScheduledFuture<?> task = resendScheduler.scheduleWithFixedDelay(() -> {
            try {
                int current = index.getAndIncrement();
                if (current >= posts.size()) {
                    resendRunning.set(false);
                    updateStatusMessage(notifyChatId, statusMessageId, "重送完成，共 " + posts.size() + " 筆");
                    ScheduledFuture<?> currentTask = taskRef.get();
                    if (currentTask != null) {
                        currentTask.cancel(false);
                    }
                    return;
                }
                ForwardPost post = posts.get(current);
                resendPost(post, mainBotEntity);
                int sentCount = current + 1;
                if (statusMessageId != null && (sentCount % progressStep == 0 || sentCount == total)) {
                    updateStatusMessage(notifyChatId, statusMessageId, buildProgressText(sentCount, total));
                }
            } catch (Exception e) {
                log.warn("重送過程發生錯誤", e);
            }
        }, 0, appConfig.getBotResendIntervalMs(), TimeUnit.MILLISECONDS);
        taskRef.set(task);
    }

    /**
     * 依貼文內容重送到 B。
     *
     * @param post 貼文
     * @param bot Bot 實體
     */
    private void resendPost(ForwardPost post, Bot bot) {
        List<ForwardPostMedia> medias = forwardPostService.findMediaByPostId(post.getId());
        String outputText = post.getOutputText() == null || post.getOutputText().isBlank() ? post.getSerial() : post.getOutputText();
        String targetChatId = appConfig.getBotResendChannelId();
        if (targetChatId == null || targetChatId.isBlank()) {
            targetChatId = appConfig.getBotPublicChannelId();
        }
        if (medias.isEmpty()) {
            SendMessage sendMessage = SendMessage.builder()
                    .chatId(targetChatId)
                    .text(outputText)
                    .build();
            telegramBotClient.send(sendMessage, bot);
            return;
        }
        if (medias.size() == 1) {
            ForwardPostMedia media = medias.get(0);
            sendSingleMediaByType(media.getMediaType(), media.getFileId(), outputText, bot, targetChatId);
            return;
        }
        List<InputMedia> inputMedias = buildMediaGroupMediasFromRecords(medias, outputText);
        if (inputMedias.isEmpty()) {
            return;
        }
        SendMediaGroup sendMediaGroup = SendMediaGroup.builder()
                .chatId(targetChatId)
                .medias(inputMedias)
                .build();
        telegramBotClient.send(sendMediaGroup, bot);
    }

    /**
     * 發送管理員提示訊息。
     *
     * @param chatId 聊天 ID
     * @param text 內容
     */
    private void sendAdminNotice(String chatId, String text) {
        Bot mainBotEntity = botService.findByBotType(BotType.MAIN);
        SendMessage sendMessage = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .build();
        telegramBotClient.send(sendMessage, mainBotEntity);
    }

    /**
     * 更新狀態訊息，若無可編輯訊息則改為送出新訊息。
     *
     * @param chatId 聊天 ID
     * @param messageId 訊息 ID
     * @param text 內容
     */
    private void updateStatusMessage(String chatId, Integer messageId, String text) {
        if (messageId == null) {
            sendAdminNotice(chatId, text);
            return;
        }
        Bot mainBotEntity = botService.findByBotType(BotType.MAIN);
        EditMessageText editText = EditMessageText.builder()
                .chatId(chatId)
                .messageId(messageId)
                .text(text)
                .build();
        telegramBotClient.send(editText, mainBotEntity);
    }

    /**
     * 建立進度文字。
     *
     * @param current 已完成數量
     * @param total 總數
     * @return 進度文字
     */
    private String buildProgressText(int current, int total) {
        if (total <= 0) {
            return "重送中";
        }
        int percent = (int) Math.round((current * 100.0) / total);
        return "重送中 " + current + "/" + total + " (" + percent + "%)";
    }
}
