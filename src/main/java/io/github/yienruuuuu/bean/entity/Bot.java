package io.github.yienruuuuu.bean.entity;

import io.github.yienruuuuu.bean.enums.BotType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "bot", schema = "tg_manager_bot")
public class Bot extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type")
    private BotType type;

    @Column(name = "bot_token")
    private String botToken;

    @Column(name = "description")
    private String description;

    @Column(name = "bot_telegram_user_name")
    private String botTelegramUserName;
}
