package io.github.yienruuuuu.repository;

import io.github.yienruuuuu.bean.entity.ForwardPost;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * @author Eric.Lee
 * Date: 2026/01/23
 */
public interface ForwardPostRepository extends JpaRepository<ForwardPost, String> {
    /**
     * 依建立時間升冪查詢所有貼文。
     *
     * @return 貼文列表
     */
    List<ForwardPost> findAllByOrderByCreatedAtAsc();
}
