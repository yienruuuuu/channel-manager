package io.github.yienruuuuu.service.business.impl;

import io.github.yienruuuuu.repository.PromoContentRepository;
import io.github.yienruuuuu.service.business.PromoContentService;
import org.springframework.stereotype.Service;

@Service
public class PromoContentServiceImpl implements PromoContentService {
    private final PromoContentRepository promoContentRepository;

    public PromoContentServiceImpl(PromoContentRepository promoContentRepository) {
        this.promoContentRepository = promoContentRepository;
    }

    @Override
    public String pickRandomContent() {
        return promoContentRepository.findRandomEnabled()
                .map(content -> content.getContent() == null ? "" : content.getContent().trim())
                .orElse("");
    }
}
