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
