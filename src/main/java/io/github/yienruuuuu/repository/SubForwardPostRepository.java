package io.github.yienruuuuu.repository;

import io.github.yienruuuuu.bean.entity.SubForwardPost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/**
 * @author Eric.Lee
 * Date: 2026/02/01
 */
public interface SubForwardPostRepository extends JpaRepository<SubForwardPost, String> {
    /**
     * 依建立時間升冪查詢所有貼文。
     *
     * @return 貼文列表
     */
    List<SubForwardPost> findAllByOrderByCreatedAtAsc();

    SubForwardPost findTopBySerialStartingWithOrderBySerialDesc(String serialPrefix);

    @Query("select p.outputText from SubForwardPost p where p.outputText is not null and p.outputText <> ''")
    List<String> findAllOutputText();
}
