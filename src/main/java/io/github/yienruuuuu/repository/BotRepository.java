package io.github.yienruuuuu.repository;

import io.github.yienruuuuu.bean.entity.Bot;
import io.github.yienruuuuu.bean.enums.BotType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BotRepository extends JpaRepository<Bot, Integer> {
    /**
     * 依據主鍵查詢 Bot。
     *
     * @param id Bot 主鍵
     * @return Bot 實體
     */
    Bot findBotById(Integer id);

    /**
     * 依據 Bot 類型查詢 Bot。
     *
     * @param type Bot 類型
     * @return Bot 實體
     */
    Bot findBotByType(BotType type);
}
