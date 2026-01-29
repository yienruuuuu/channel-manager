package io.github.yienruuuuu.bean.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * @author Eric.Lee
 * Date: 2026/01/23
 */
@Getter
@Setter
@Entity
@Table(name = "forward_post", schema = "tg_manager_bot")
public class ForwardPost extends BaseEntity {
    @Id
    @Column(name = "id", nullable = false, length = 36)
    private String id;

    @Column(name = "serial", nullable = false, length = 32)
    private String serial;

    @Column(name = "source_chat_id", length = 64)
    private String sourceChatId;

    @Column(name = "source_message_id")
    private Integer sourceMessageId;

    @Column(name = "source_media_group_id", length = 64)
    private String sourceMediaGroupId;

    @Column(name = "forward_from_chat_id", length = 64)
    private String forwardFromChatId;

    @Column(name = "forward_from_chat_title", length = 255)
    private String forwardFromChatTitle;

    @Column(name = "forward_from_user_id", length = 64)
    private String forwardFromUserId;

    @Column(name = "forward_from_user_username", length = 255)
    private String forwardFromUserUsername;

    @Column(name = "forward_from_user_name", length = 255)
    private String forwardFromUserName;

    @Column(name = "original_text", columnDefinition = "TEXT")
    private String originalText;

    @Column(name = "processed_text", columnDefinition = "TEXT")
    private String processedText;

    @Column(name = "output_text", columnDefinition = "TEXT")
    private String outputText;
}
