package io.github.yienruuuuu.service.business.impl;

import io.github.yienruuuuu.bean.entity.Wish;
import io.github.yienruuuuu.repository.WishRepository;
import io.github.yienruuuuu.service.business.WishService;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * @author Eric.Lee
 * Date: 2026/02/02
 */
@Service
public class WishServiceImpl implements WishService {
    private final WishRepository wishRepository;

    public WishServiceImpl(WishRepository wishRepository) {
        this.wishRepository = wishRepository;
    }

    @Override
    public Wish save(Wish wish) {
        return wishRepository.save(wish);
    }

    @Override
    public long countByUserIdAndInsDatBetween(Long userId, Instant start, Instant end) {
        return wishRepository.countByUserIdAndInsDatBetween(userId, start, end);
    }
}
