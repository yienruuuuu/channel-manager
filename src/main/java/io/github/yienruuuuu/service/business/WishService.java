package io.github.yienruuuuu.service.business;

import io.github.yienruuuuu.bean.entity.Wish;

import java.time.Instant;

/**
 * @author Eric.Lee
 * Date: 2026/02/02
 */
public interface WishService {
    Wish save(Wish wish);

    long countByUserIdAndInsDatBetween(Long userId, Instant start, Instant end);
}
