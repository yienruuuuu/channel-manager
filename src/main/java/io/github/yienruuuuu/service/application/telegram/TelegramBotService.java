package io.github.yienruuuuu.service.application.telegram;

import io.github.yienruuuuu.bean.entity.Bot;
import io.github.yienruuuuu.bean.enums.BotType;
import io.github.yienruuuuu.repository.BotRepository;
import io.github.yienruuuuu.service.application.telegram.cashier_bot.CashierBotConsumer;
import io.github.yienruuuuu.service.application.telegram.main_bot.MainBotConsumer;
import io.github.yienruuuuu.service.application.telegram.sub_bot.SubBotConsumer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.GetMe;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.List;
/**
 * @author Eric.Lee
 * Date: 2026/01/23
 */
@Slf4j
@Service
public class TelegramBotService {
    //TG長輪巡物件
    private TelegramBotsLongPollingApplication botsApplication;
    //Repo
    private final BotRepository botRepository;
    //Telegram Client傳訊物件
    private final TelegramBotClient telegramBotClient;
    private final MainBotConsumer mainBotConsumer;
    private final CashierBotConsumer cashierBotConsumer;
    private final SubBotConsumer subBotConsumer;

    /**
     * 建立 TelegramBotService，注入主要依賴。
     *
     * @param mainBotConsumer 主要更新消費者
     * @param botRepository   Bot 資料存取物件
     * @param telegramBotClient Telegram API 呼叫封裝
     */
    public TelegramBotService(
            MainBotConsumer mainBotConsumer,
            CashierBotConsumer cashierBotConsumer,
            SubBotConsumer subBotConsumer,
            BotRepository botRepository,
            TelegramBotClient telegramBotClient
    ) {
        this.botRepository = botRepository;
        this.telegramBotClient = telegramBotClient;
        this.mainBotConsumer = mainBotConsumer;
        this.cashierBotConsumer = cashierBotConsumer;
        this.subBotConsumer = subBotConsumer;
    }

    /**
     * 啟動後註冊機器人並更新資料庫中的 Bot 資訊。
     */
    @PostConstruct
    public void registerBots() {
        // 初始化 TG 長輪詢應用
        botsApplication = new TelegramBotsLongPollingApplication();
        List<Bot> bots = botRepository.findAll();
        if (bots.isEmpty()) {
            log.warn("未找到任何 Bot 設定，無法註冊");
            return;
        }
        for (Bot botEntity : bots) {
            if (botEntity == null || botEntity.getBotToken() == null || botEntity.getBotToken().isBlank()) {
                log.warn("未設定 BOT TOKEN，BotType: {}", botEntity == null ? null : botEntity.getType());
                continue;
            }
            var consumer = resolveConsumer(botEntity.getType());
            if (consumer == null) {
                log.warn("未找到對應的 consumer，BotType: {}", botEntity.getType());
                continue;
            }
            try {
                botsApplication.registerBot(botEntity.getBotToken(), consumer);
                log.info("機器人 {} 註冊完成", botEntity.getType());
            } catch (TelegramApiException e) {
                log.error("機器人 {} 註冊發生錯誤 , 錯誤訊息 : ", botEntity.getType(), e);
                continue;
            }
            // 更新資料庫中的 Bot 資料設定
            updateBotData(botEntity);
            // 建立指令
            if (BotType.MAIN.equals(botEntity.getType())) {
                registerBotCommands(botEntity);
            }
            if (BotType.CASHIER.equals(botEntity.getType())) {
                registerCashierCommands(botEntity);
            }
            if (BotType.SUB.equals(botEntity.getType())) {
                registerSubBotCommands(botEntity);
            }
        }
    }

    /**
     * 取得機器人自我資訊並回寫資料庫。
     *
     * @param botEntity 需要更新的 Bot 實體
     */
    private void updateBotData(Bot botEntity) {
        User botData = telegramBotClient.send(GetMe.builder().build(), botEntity);
        if (botData == null) {
            return;
        }
        botEntity.setBotTelegramUserName(botData.getUserName());
        botRepository.save(botEntity);
    }

    /**
     * 註冊機器人指令，讓使用者在對話視窗快速選取。
     *
     * @param botEntity 需要設定指令的 Bot
     */
    private void registerBotCommands(Bot botEntity) {
        SetMyCommands setMyCommands = SetMyCommands.builder()
                .commands(
                        List.of(
                                new BotCommand("/resend", "重送全部歷史貼文到指定頻道")
                        )
                )
                .build();
        telegramBotClient.send(setMyCommands, botEntity);
    }

    private void registerCashierCommands(Bot botEntity) {
        SetMyCommands setMyCommands = SetMyCommands.builder()
                .commands(
                        List.of(
                                new BotCommand("/start", "取得收費連結"),
                                new BotCommand("/paid", "重新取得收費連結"),
                                new BotCommand("/wish", "許願 (格式：/wish 平台_人名_類型_其他)")
                        )
                )
                .build();
        telegramBotClient.send(setMyCommands, botEntity);
    }

    private void registerSubBotCommands(Bot botEntity) {
        SetMyCommands setMyCommands = SetMyCommands.builder()
                .commands(
                        List.of(
                                new BotCommand("/resend", "重送全部歷史貼文到指定頻道")
                        )
                )
                .build();
        telegramBotClient.send(setMyCommands, botEntity);
    }

    private LongPollingSingleThreadUpdateConsumer resolveConsumer(BotType type) {
        if (type == null) {
            return null;
        }
        if (BotType.MAIN.equals(type)) {
            return mainBotConsumer;
        }
        if (BotType.CASHIER.equals(type)) {
            return cashierBotConsumer;
        }
        if (BotType.SUB.equals(type)) {
            return subBotConsumer;
        }
        return null;
    }

    /**
     * 關閉長輪詢並釋放資源。
     *
     * @throws Exception 關閉過程可能拋出的例外
     */
    @PreDestroy
    public void shutdownBot() throws Exception {
        if (botsApplication != null) {
            log.info("關閉機器人並釋放資源");
            botsApplication.close();
        }
    }
}
