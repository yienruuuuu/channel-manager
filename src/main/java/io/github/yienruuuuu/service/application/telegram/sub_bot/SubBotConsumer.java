package io.github.yienruuuuu.service.application.telegram.sub_bot;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.yienruuuuu.bean.entity.Bot;
import io.github.yienruuuuu.bean.entity.SubForwardPost;
import io.github.yienruuuuu.bean.entity.SubForwardPostMedia;
import io.github.yienruuuuu.bean.enums.BotType;
import io.github.yienruuuuu.config.AppConfig;
import io.github.yienruuuuu.service.application.telegram.TelegramBotClient;
import io.github.yienruuuuu.service.business.BotService;
import io.github.yienruuuuu.service.business.SubForwardPostService;
import io.github.yienruuuuu.service.business.model.ForwardPostMediaItem;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendAnimation;
import org.telegram.telegrambots.meta.api.methods.send.SendAudio;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMediaGroup;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.send.SendVideo;
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
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Eric.Lee
 * Date: 2026/02/01
 */
@Component
@Slf4j
public class SubBotConsumer implements LongPollingSingleThreadUpdateConsumer {
    private static final long MEDIA_GROUP_FLUSH_DELAY_MS = 2000L;
    private static final DateTimeFormatter SERIAL_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String SERIAL_PREFIX = "SUB-";
    private static final String CALLBACK_RESEND_ALL = "sub_resend_all";
    private final BotService botService;
    private final SubForwardPostService subForwardPostService;
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

    @Autowired
    public SubBotConsumer(
            BotService botService,
            SubForwardPostService subForwardPostService,
            AppConfig appConfig,
            TelegramBotClient telegramBotClient,
            ObjectMapper objectMapper
    ) {
        this.botService = botService;
        this.subForwardPostService = subForwardPostService;
        this.appConfig = appConfig;
        this.telegramBotClient = telegramBotClient;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    private void initSerialCounter() {
        synchronized (serialLock) {
            LocalDate today = LocalDate.now();
            String prefix = SERIAL_PREFIX + SERIAL_DATE_FORMAT.format(today) + "_";
            String latestSerial = subForwardPostService.findLatestSerialByPrefix(prefix);
            currentSerialDate = today;
            currentSerial = parseSerialIndex(latestSerial);
            if (currentSerial > 0) {
                log.info("子機器人序號初始化完成，最後序號 {}", latestSerial);
            }
        }
    }

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
        String communicateChannelId = appConfig.getSubCommunicateChannelChatId();
        if (isBlank(communicateChannelId)) {
            return;
        }
        String publicChannelId = appConfig.getSubPublicChannelId();
        if (isBlank(publicChannelId)) {
            return;
        }
        Message channelPost = update.getChannelPost();
        String sourceChannelId = String.valueOf(channelPost.getChatId());
        if (!sourceChannelId.equals(communicateChannelId)) {
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

    private void logIncomingUpdateJson(Update update) {
        try {
            String json = objectMapper.writeValueAsString(update);
            log.debug("收到更新 json={}", json);
        } catch (JsonProcessingException e) {
            log.warn("無法序列化 Update 成 JSON", e);
        }
    }

    private void sendTextMessage(Message channelPost, String sourceChannelId) {
        String serial = nextSerial();
        String originalText = channelPost.getText();
        String outputText = originalText;
        ForwardFromUserInfo forwardFromUserInfo = extractForwardFromUserInfo(channelPost);
        String forwardFromChatId = channelPost.getForwardFromChat() == null
                ? null
                : String.valueOf(channelPost.getForwardFromChat().getId());
        String forwardFromChatTitle = channelPost.getForwardFromChat() == null
                ? null
                : channelPost.getForwardFromChat().getTitle();
        String forwardFromUserId = forwardFromUserInfo == null ? null : forwardFromUserInfo.id;
        String forwardFromUserUsername = forwardFromUserInfo == null ? null : forwardFromUserInfo.username;
        String forwardFromUserName = forwardFromUserInfo == null ? null : forwardFromUserInfo.name;
        Bot subBotEntity = botService.findByBotType(BotType.SUB);

        SendMessage sendMessage = SendMessage.builder()
                .chatId(appConfig.getSubPublicChannelId())
                .text(outputText)
                .build();
        telegramBotClient.send(sendMessage, subBotEntity);

        SubForwardPost post = subForwardPostService.createPost(
                serial,
                sourceChannelId,
                channelPost.getMessageId(),
                channelPost.getMediaGroupId(),
                forwardFromChatId,
                forwardFromChatTitle,
                forwardFromUserId,
                forwardFromUserUsername,
                forwardFromUserName,
                originalText,
                originalText,
                outputText,
                List.of()
        );
        sendAcknowledgement(serial, post.getId(), channelPost.getMessageId(), subBotEntity);
        log.info("子機器人已發送文字序號 {} 對應來源 {}，送達 {}", serial, sourceChannelId, appConfig.getSubPublicChannelId());
    }

    private void sendSingleMediaMessage(Message channelPost, String sourceChannelId) {
        if (isDuplicateMediaMessage(channelPost)) {
            sendDuplicateNotice(channelPost.getMessageId());
            return;
        }
        String serial = nextSerial();
        String originalText = channelPost.getCaption();
        String outputText = originalText;
        ForwardFromUserInfo forwardFromUserInfo = extractForwardFromUserInfo(channelPost);
        String forwardFromChatId = channelPost.getForwardFromChat() == null
                ? null
                : String.valueOf(channelPost.getForwardFromChat().getId());
        String forwardFromChatTitle = channelPost.getForwardFromChat() == null
                ? null
                : channelPost.getForwardFromChat().getTitle();
        String forwardFromUserId = forwardFromUserInfo == null ? null : forwardFromUserInfo.id;
        String forwardFromUserUsername = forwardFromUserInfo == null ? null : forwardFromUserInfo.username;
        String forwardFromUserName = forwardFromUserInfo == null ? null : forwardFromUserInfo.name;
        Bot subBotEntity = botService.findByBotType(BotType.SUB);
        boolean sent = sendSingleMedia(channelPost, outputText, subBotEntity);
        if (!sent) {
            log.warn("不支援的媒體型別，略過訊息 {}", channelPost.getMessageId());
            return;
        }

        SubForwardPost post = subForwardPostService.createPost(
                serial,
                sourceChannelId,
                channelPost.getMessageId(),
                channelPost.getMediaGroupId(),
                forwardFromChatId,
                forwardFromChatTitle,
                forwardFromUserId,
                forwardFromUserUsername,
                forwardFromUserName,
                originalText,
                originalText,
                outputText,
                buildMediaItemsFromMessage(channelPost)
        );
        sendAcknowledgement(serial, post.getId(), channelPost.getMessageId(), subBotEntity);
        log.info("子機器人已發送序號 {} 對應來源 {}，送達 {}", serial, sourceChannelId, appConfig.getSubPublicChannelId());
    }

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
        String outputText = originalText;
        ForwardFromUserInfo forwardFromUserInfo = extractForwardFromUserInfo(messages.get(0));
        Message firstMessage = messages.get(0);
        String forwardFromChatId = firstMessage.getForwardFromChat() == null
                ? null
                : String.valueOf(firstMessage.getForwardFromChat().getId());
        String forwardFromChatTitle = firstMessage.getForwardFromChat() == null
                ? null
                : firstMessage.getForwardFromChat().getTitle();
        String forwardFromUserId = forwardFromUserInfo == null ? null : forwardFromUserInfo.id;
        String forwardFromUserUsername = forwardFromUserInfo == null ? null : forwardFromUserInfo.username;
        String forwardFromUserName = forwardFromUserInfo == null ? null : forwardFromUserInfo.name;
        Bot subBotEntity = botService.findByBotType(BotType.SUB);
        List<InputMedia> medias = buildMediaGroupMedias(messages, outputText);
        if (medias.isEmpty()) {
            log.warn("media group {} 無可用媒體，略過發送", mediaGroupId);
            return;
        }
        SendMediaGroup sendMediaGroup = SendMediaGroup.builder()
                .chatId(appConfig.getSubPublicChannelId())
                .medias(medias)
                .build();
        telegramBotClient.send(sendMediaGroup, subBotEntity);

        SubForwardPost post = subForwardPostService.createPost(
                serial,
                String.valueOf(messages.get(0).getChatId()),
                messages.get(0).getMessageId(),
                mediaGroupId,
                forwardFromChatId,
                forwardFromChatTitle,
                forwardFromUserId,
                forwardFromUserUsername,
                forwardFromUserName,
                originalText,
                originalText,
                outputText,
                buildMediaItemsFromMessages(messages)
        );
        sendAcknowledgement(serial, post.getId(), messages.get(0).getMessageId(), subBotEntity);
        log.info("子機器人已發送 media group {} 序號 {}，共 {} 則訊息", mediaGroupId, serial, medias.size());
    }

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

    private static class ForwardFromUserInfo {
        private final String id;
        private final String username;
        private final String name;
        private final boolean isBot;

        private ForwardFromUserInfo(String id, String username, String name, boolean isBot) {
            this.id = id;
            this.username = username;
            this.name = name;
            this.isBot = isBot;
        }
    }

    private String nextSerial() {
        synchronized (serialLock) {
            LocalDate today = LocalDate.now();
            if (!today.equals(currentSerialDate)) {
                currentSerialDate = today;
                currentSerial = 0;
            }
            int next = ++currentSerial;
            return SERIAL_PREFIX + SERIAL_DATE_FORMAT.format(today) + "_" + String.format("%04d", next);
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

    private boolean sendSingleMedia(Message message, String caption, Bot bot) {
        String chatId = appConfig.getSubPublicChannelId();
        if (message.hasPhoto()) {
            List<PhotoSize> photos = message.getPhoto();
            if (photos == null || photos.isEmpty()) {
                return false;
            }
            String fileId = photos.get(photos.size() - 1).getFileId();
            SendPhoto.SendPhotoBuilder builder = SendPhoto.builder()
                    .chatId(chatId)
                    .photo(new InputFile(fileId));
            if (!isBlank(caption)) {
                builder.caption(caption);
            }
            telegramBotClient.send(builder.build(), bot);
            return true;
        }
        if (message.hasVideo()) {
            String fileId = message.getVideo().getFileId();
            SendVideo.SendVideoBuilder builder = SendVideo.builder()
                    .chatId(chatId)
                    .video(new InputFile(fileId));
            if (!isBlank(caption)) {
                builder.caption(caption);
            }
            telegramBotClient.send(builder.build(), bot);
            return true;
        }
        if (message.hasDocument()) {
            String fileId = message.getDocument().getFileId();
            SendDocument.SendDocumentBuilder builder = SendDocument.builder()
                    .chatId(chatId)
                    .document(new InputFile(fileId));
            if (!isBlank(caption)) {
                builder.caption(caption);
            }
            telegramBotClient.send(builder.build(), bot);
            return true;
        }
        if (message.hasAudio()) {
            String fileId = message.getAudio().getFileId();
            SendAudio.SendAudioBuilder builder = SendAudio.builder()
                    .chatId(chatId)
                    .audio(new InputFile(fileId));
            if (!isBlank(caption)) {
                builder.caption(caption);
            }
            telegramBotClient.send(builder.build(), bot);
            return true;
        }
        if (message.hasAnimation()) {
            String fileId = message.getAnimation().getFileId();
            SendAnimation.SendAnimationBuilder builder = SendAnimation.builder()
                    .chatId(chatId)
                    .animation(new InputFile(fileId));
            if (!isBlank(caption)) {
                builder.caption(caption);
            }
            telegramBotClient.send(builder.build(), bot);
            return true;
        }
        return false;
    }

    private List<InputMedia> buildMediaGroupMedias(List<Message> messages, String caption) {
        List<InputMedia> medias = new ArrayList<>();
        boolean captionApplied = false;
        for (Message message : messages) {
            InputMedia media = buildInputMedia(message);
            if (media == null) {
                continue;
            }
            if (!captionApplied && !isBlank(caption)) {
                media.setCaption(caption);
                captionApplied = true;
            }
            medias.add(media);
        }
        return medias;
    }

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

    private ForwardFromUserInfo extractForwardFromUserInfo(Message message) {
        if (message == null || message.getForwardFrom() == null) {
            return null;
        }
        User user = message.getForwardFrom();
        String id = user.getId() == null ? null : String.valueOf(user.getId());
        String username = user.getUserName();
        String name = buildUserDisplayName(user);
        boolean isBot = Boolean.TRUE.equals(user.getIsBot());
        if (isBlank(id) && isBlank(username) && isBlank(name)) {
            return null;
        }
        return new ForwardFromUserInfo(id, username, name, isBot);
    }

    private String buildUserDisplayName(User user) {
        if (user == null) {
            return null;
        }
        String first = user.getFirstName();
        String last = user.getLastName();
        if (isBlank(first) && isBlank(last)) {
            return null;
        }
        if (isBlank(last)) {
            return first == null ? null : first.trim();
        }
        if (isBlank(first)) {
            return last == null ? null : last.trim();
        }
        return (first + " " + last).trim();
    }

    private List<ForwardPostMediaItem> buildMediaItemsFromMessage(Message message) {
        List<ForwardPostMediaItem> items = new ArrayList<>();
        ForwardPostMediaItem item = buildMediaItem(message);
        if (item != null) {
            items.add(item);
        }
        return items;
    }

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
        return subForwardPostService.existsByMediaFileId(item.getFileId());
    }

    private boolean isDuplicateMediaGroup(List<Message> messages) {
        List<ForwardPostMediaItem> items = buildMediaItemsFromMessages(messages);
        for (ForwardPostMediaItem item : items) {
            if (item.getFileId() == null || item.getFileId().isBlank()) {
                continue;
            }
            if (subForwardPostService.existsByMediaFileId(item.getFileId())) {
                return true;
            }
        }
        return false;
    }

    private void sendAcknowledgement(String serial, String postId, Integer replyToMessageId, Bot bot) {
        SendMessage sendMessage = SendMessage.builder()
                .chatId(appConfig.getSubCommunicateChannelChatId())
                .text(serial)
                .replyToMessageId(replyToMessageId)
                .build();
        telegramBotClient.send(sendMessage, bot);
    }

    private void sendDuplicateNotice(Integer replyToMessageId) {
        Bot subBotEntity = botService.findByBotType(BotType.SUB);
        SendMessage sendMessage = SendMessage.builder()
                .chatId(appConfig.getSubCommunicateChannelChatId())
                .text("重複轉傳")
                .replyToMessageId(replyToMessageId)
                .build();
        telegramBotClient.send(sendMessage, subBotEntity);
    }

    private void handleCallbackQuery(Update update) {
        String adminId = appConfig.getBotAdminId();
        if (isBlank(adminId)) {
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
            telegramBotClient.send(editText, botService.findByBotType(BotType.SUB));
            if (update.getCallbackQuery().getMessage() instanceof Message callbackMessage
                    && callbackMessage.getReplyMarkup() != null) {
                EditMessageReplyMarkup clearMarkup = EditMessageReplyMarkup.builder()
                        .chatId(chatId)
                        .messageId(statusMessageId)
                        .build();
                telegramBotClient.send(clearMarkup, botService.findByBotType(BotType.SUB));
            }
        }
        String chatId = update.getCallbackQuery().getMessage() == null
                ? String.valueOf(update.getCallbackQuery().getFrom().getId())
                : String.valueOf(update.getCallbackQuery().getMessage().getChatId());
        startResendAll(chatId, statusMessageId);
    }

    private void answerCallback(String callbackQueryId, String text) {
        Bot subBotEntity = botService.findByBotType(BotType.SUB);
        AnswerCallbackQuery answer = AnswerCallbackQuery.builder()
                .callbackQueryId(callbackQueryId)
                .text(text)
                .build();
        telegramBotClient.send(answer, subBotEntity);
    }

    private void handleAdminMessage(Message message) {
        if (message.getChat() == null || !"private".equals(message.getChat().getType())) {
            return;
        }
        String adminId = appConfig.getBotAdminId();
        if (isBlank(adminId)) {
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
        Bot subBotEntity = botService.findByBotType(BotType.SUB);
        telegramBotClient.send(prompt, subBotEntity);
    }

    private void startResendAll(String notifyChatId, Integer statusMessageId) {
        if (!resendRunning.compareAndSet(false, true)) {
            updateStatusMessage(notifyChatId, statusMessageId, "重送中，請稍後再試");
            return;
        }
        if (isBlank(appConfig.getSubPublicChannelId()) && isBlank(appConfig.getSubResendChannelId())) {
            resendRunning.set(false);
            updateStatusMessage(notifyChatId, statusMessageId, "未設定子機器人目標頻道");
            return;
        }
        List<SubForwardPost> posts = subForwardPostService.findAllOrderByCreatedAtAsc();
        if (posts.isEmpty()) {
            resendRunning.set(false);
            updateStatusMessage(notifyChatId, statusMessageId, "沒有可重送的歷史紀錄");
            return;
        }
        Bot subBotEntity = botService.findByBotType(BotType.SUB);
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
                SubForwardPost post = posts.get(current);
                resendPost(post, subBotEntity);
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

    private void resendPost(SubForwardPost post, Bot bot) {
        List<SubForwardPostMedia> medias = subForwardPostService.findMediaByPostId(post.getId());
        String outputText = post.getOutputText();
        String targetChatId = appConfig.getSubResendChannelId();
        if (isBlank(targetChatId)) {
            targetChatId = appConfig.getSubPublicChannelId();
        }
        if (medias.isEmpty()) {
            if (isBlank(outputText)) {
                return;
            }
            SendMessage sendMessage = SendMessage.builder()
                    .chatId(targetChatId)
                    .text(outputText)
                    .build();
            telegramBotClient.send(sendMessage, bot);
            return;
        }
        if (medias.size() == 1) {
            SubForwardPostMedia media = medias.get(0);
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

    private void sendSingleMediaByType(String mediaType, String fileId, String caption, Bot bot, String chatId) {
        if ("photo".equals(mediaType)) {
            SendPhoto.SendPhotoBuilder builder = SendPhoto.builder()
                    .chatId(chatId)
                    .photo(new InputFile(fileId));
            if (!isBlank(caption)) {
                builder.caption(caption);
            }
            telegramBotClient.send(builder.build(), bot);
            return;
        }
        if ("video".equals(mediaType)) {
            SendVideo.SendVideoBuilder builder = SendVideo.builder()
                    .chatId(chatId)
                    .video(new InputFile(fileId));
            if (!isBlank(caption)) {
                builder.caption(caption);
            }
            telegramBotClient.send(builder.build(), bot);
            return;
        }
        if ("document".equals(mediaType)) {
            SendDocument.SendDocumentBuilder builder = SendDocument.builder()
                    .chatId(chatId)
                    .document(new InputFile(fileId));
            if (!isBlank(caption)) {
                builder.caption(caption);
            }
            telegramBotClient.send(builder.build(), bot);
            return;
        }
        if ("audio".equals(mediaType)) {
            SendAudio.SendAudioBuilder builder = SendAudio.builder()
                    .chatId(chatId)
                    .audio(new InputFile(fileId));
            if (!isBlank(caption)) {
                builder.caption(caption);
            }
            telegramBotClient.send(builder.build(), bot);
            return;
        }
        if ("animation".equals(mediaType)) {
            SendAnimation.SendAnimationBuilder builder = SendAnimation.builder()
                    .chatId(chatId)
                    .animation(new InputFile(fileId));
            if (!isBlank(caption)) {
                builder.caption(caption);
            }
            telegramBotClient.send(builder.build(), bot);
        }
    }

    private List<InputMedia> buildMediaGroupMediasFromRecords(List<SubForwardPostMedia> medias, String caption) {
        List<InputMedia> inputs = new ArrayList<>();
        boolean captionApplied = false;
        for (SubForwardPostMedia media : medias) {
            InputMedia input = buildInputMediaFromRecord(media);
            if (input == null) {
                continue;
            }
            if (!captionApplied && !isBlank(caption)) {
                input.setCaption(caption);
                captionApplied = true;
            }
            inputs.add(input);
        }
        return inputs;
    }

    private InputMedia buildInputMediaFromRecord(SubForwardPostMedia media) {
        String mediaType = media.getMediaType();
        String fileId = media.getFileId();
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

    private void sendAdminNotice(String chatId, String text) {
        Bot subBotEntity = botService.findByBotType(BotType.SUB);
        SendMessage sendMessage = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .build();
        telegramBotClient.send(sendMessage, subBotEntity);
    }

    private void updateStatusMessage(String chatId, Integer messageId, String text) {
        if (messageId == null) {
            sendAdminNotice(chatId, text);
            return;
        }
        Bot subBotEntity = botService.findByBotType(BotType.SUB);
        EditMessageText editText = EditMessageText.builder()
                .chatId(chatId)
                .messageId(messageId)
                .text(text)
                .build();
        telegramBotClient.send(editText, subBotEntity);
    }

    private String buildProgressText(int current, int total) {
        if (total <= 0) {
            return "重送中";
        }
        int percent = (int) Math.round((current * 100.0) / total);
        return "重送中 " + current + "/" + total + " (" + percent + "%)";
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
