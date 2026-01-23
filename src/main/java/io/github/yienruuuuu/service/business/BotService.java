package io.github.yienruuuuu.service.business;

import io.github.yienruuuuu.bean.entity.Bot;
import io.github.yienruuuuu.bean.enums.BotType;

/**
 * @author Eric.Lee
 * Date: 2026/01/23
 */
public interface BotService {
    /**
     * 依據 Bot 類型取得 Bot。
     *
     * @param type Bot 類型
     * @return Bot 實體
     */
    Bot findByBotType(BotType type);
}
