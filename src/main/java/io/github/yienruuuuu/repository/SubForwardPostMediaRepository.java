package io.github.yienruuuuu.repository;

import io.github.yienruuuuu.bean.entity.SubForwardPostMedia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * @author Eric.Lee
 * Date: 2026/02/01
 */
public interface SubForwardPostMediaRepository extends JpaRepository<SubForwardPostMedia, Integer> {
    /**
     * 依據貼文 ID 取得媒體列表，依排序回傳。
     *
     * @param postId 貼文 ID
     * @return 媒體列表
     */
    List<SubForwardPostMedia> findByPostIdOrderBySortOrderAsc(String postId);

    /**
     * 判斷指定檔案 ID 是否存在。
     *
     * @param fileId 檔案 ID
     * @return true 表示已存在
     */
    boolean existsByFileId(String fileId);
}
