package io.github.yienruuuuu.service.business.impl;

import io.github.yienruuuuu.bean.entity.Bot;
import io.github.yienruuuuu.bean.enums.BotType;
import io.github.yienruuuuu.repository.BotRepository;
import io.github.yienruuuuu.service.business.BotService;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Eric.Lee
 * Date: 2026/01/23
 */
@Service("botService")
public class BotServiceImpl implements BotService {
    private final BotRepository botRepository;
    private final ConcurrentHashMap<BotType, Bot> botCache = new ConcurrentHashMap<>();

    /**
     * 建立服務並初始化快取。
     *
     * @param botRepository Bot 資料存取物件
     */
    public BotServiceImpl(BotRepository botRepository) {
        this.botRepository = botRepository;
        initializeCache();
    }

    /**
     * 由快取取得 Bot，若快取缺失則回查資料庫並回填。
     *
     * @param type Bot 類型
     * @return Bot 實體
     */
    @Override
    public Bot findByBotType(BotType type) {
        return botCache.computeIfAbsent(type, botRepository::findBotByType);
    }

    /**
     * 啟動時將現有 Bot 一次載入快取，避免頻繁查詢。
     */
    private void initializeCache() {
        botRepository.findAll().forEach(bot -> botCache.put(bot.getType(), bot));
    }
}
