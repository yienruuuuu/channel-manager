package io.github.yienruuuuu.service.business.impl;

import io.github.yienruuuuu.repository.ChannelSuffixRepository;
import io.github.yienruuuuu.service.business.ChannelSuffixService;
import org.springframework.stereotype.Service;

@Service
public class ChannelSuffixServiceImpl implements ChannelSuffixService {
    private final ChannelSuffixRepository channelSuffixRepository;

    public ChannelSuffixServiceImpl(ChannelSuffixRepository channelSuffixRepository) {
        this.channelSuffixRepository = channelSuffixRepository;
    }

    @Override
    public String pickSuffixByForwardFromChatId(String forwardFromChatId) {
        if (forwardFromChatId == null || forwardFromChatId.isBlank()) {
            return "";
        }
        return channelSuffixRepository.findRandomEnabledByForwardFromChatId(forwardFromChatId)
                .map(suffix -> suffix.getSuffixText() == null ? "" : suffix.getSuffixText().trim())
                .orElse("");
    }
}
