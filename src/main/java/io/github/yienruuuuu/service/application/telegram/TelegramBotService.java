package io.github.yienruuuuu.service.application.telegram;

import io.github.yienruuuuu.bean.entity.Bot;
import io.github.yienruuuuu.bean.enums.BotType;
import io.github.yienruuuuu.repository.BotRepository;
import io.github.yienruuuuu.service.application.telegram.main_bot.MainBotConsumer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.meta.api.methods.GetMe;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

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

    /**
     * 建立 TelegramBotService，注入主要依賴。
     *
     * @param mainBotConsumer 主要更新消費者
     * @param botRepository   Bot 資料存取物件
     * @param telegramBotClient Telegram API 呼叫封裝
     */
    public TelegramBotService(
            MainBotConsumer mainBotConsumer,
            BotRepository botRepository,
            TelegramBotClient telegramBotClient
    ) {
        this.botRepository = botRepository;
        this.telegramBotClient = telegramBotClient;
        this.mainBotConsumer = mainBotConsumer;
    }

    /**
     * 啟動後註冊機器人並更新資料庫中的 Bot 資訊。
     */
    @PostConstruct
    public void registerBots() {
        // 初始化 TG 長輪詢應用
        botsApplication = new TelegramBotsLongPollingApplication();
        Bot botEntity = botRepository.findBotByType(BotType.MAIN);
        if (botEntity == null || botEntity.getBotToken() == null || botEntity.getBotToken().isBlank()) {
            log.warn("未找到對應的 BOT 實體或 BOT TOKEN ， BotType: {}", BotType.MAIN);
            return;
        }
        try {
            botsApplication.registerBot(botEntity.getBotToken(), mainBotConsumer);
            log.info("機器人 {} 註冊完成", BotType.MAIN);
        } catch (TelegramApiException e) {
            log.error("機器人 {} 註冊發生錯誤 , 錯誤訊息 : ", BotType.MAIN, e);
        }
        // 更新資料庫中的 Bot 資料設定
        updateBotData(botEntity);
    }

    /**
     * 取得機器人自我資訊並回寫資料庫。
     *
     * @param botEntity 需要更新的 Bot 實體
     */
    private void updateBotData(Bot botEntity) {
        User botData = telegramBotClient.send(GetMe.builder().build(), botEntity);
        botEntity.setBotTelegramUserName(botData.getUserName());
        botRepository.save(botEntity);
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
