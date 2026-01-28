package io.github.yienruuuuu.repository;

import io.github.yienruuuuu.bean.entity.PromoContent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface PromoContentRepository extends JpaRepository<PromoContent, Integer> {
    @Query(value = "select * from tg_manager_bot.promo_content where enabled = true order by random() limit 1", nativeQuery = true)
    Optional<PromoContent> findRandomEnabled();
}
