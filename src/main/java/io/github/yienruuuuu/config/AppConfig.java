package io.github.yienruuuuu.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Eric.Lee
 * Date: 2026/01/23
 */
@Configuration
@Getter
public class AppConfig {
    @Value("${bot.communicate-channel}")
    private String botCommunicateChannelChatId;

    @Value("${bot.public-channel}")
    private String botPublicChannelId;

    @Value("${bot.resend-channel:}")
    private String botResendChannelId;

    @Value("${bot.sub.communicate-channel:}")
    private String subCommunicateChannelChatId;

    @Value("${bot.sub.public-channel:}")
    private String subPublicChannelId;

    @Value("${bot.sub.resend-channel:}")
    private String subResendChannelId;

    @Value("${bot.admin-id:}")
    private String botAdminId;

    @Value("${bot.resend-interval-ms:2000}")
    private long botResendIntervalMs;

    /**
     * 建立全域 ObjectMapper，加入 Java 8 時間序列化支援。
     *
     * @return 設定完成的 ObjectMapper
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        return objectMapper;
    }
}
