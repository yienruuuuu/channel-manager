package io.github.yienruuuuu.repository;

import io.github.yienruuuuu.bean.entity.Wish;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

/**
 * @author Eric.Lee
 * Date: 2026/02/02
 */
public interface WishRepository extends JpaRepository<Wish, String> {
    long countByUserIdAndInsDatBetween(Long userId, Instant start, Instant end);
}
