package io.github.yienruuuuu.repository;

import io.github.yienruuuuu.bean.entity.ChannelSuffix;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface ChannelSuffixRepository extends JpaRepository<ChannelSuffix, Integer> {
    @Query(value = "select * from tg_manager_bot.channel_suffix where enabled = true and forward_from_chat_id = ?1 order by random() limit 1", nativeQuery = true)
    Optional<ChannelSuffix> findRandomEnabledByForwardFromChatId(String forwardFromChatId);
}
