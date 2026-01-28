package io.github.yienruuuuu.bean.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "channel_suffix", schema = "tg_manager_bot")
public class ChannelSuffix extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "forward_from_chat_id", nullable = false, length = 64)
    private String forwardFromChatId;

    @Column(name = "suffix_text", nullable = false, columnDefinition = "TEXT")
    private String suffixText;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled;
}
