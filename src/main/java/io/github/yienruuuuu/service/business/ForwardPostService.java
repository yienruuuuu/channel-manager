package io.github.yienruuuuu.service.business;

import io.github.yienruuuuu.bean.entity.ForwardPost;
import io.github.yienruuuuu.bean.entity.ForwardPostMedia;
import io.github.yienruuuuu.service.business.model.ForwardPostMediaItem;

import java.util.List;
import java.util.Optional;

/**
 * @author Eric.Lee
 * Date: 2026/01/23
 */
public interface ForwardPostService {
    /**
     * 建立貼文與媒體記錄。
     *
     * @param serial 序號
     * @param sourceChatId 來源聊天 ID
     * @param sourceMessageId 來源訊息 ID
     * @param sourceMediaGroupId 來源 media group ID
     * @param originalText 原始文字
     * @param processedText 處理後文字
     * @param outputText 最終輸出文字
     * @param mediaItems 媒體項目
     * @return 建立完成的貼文
     */
    ForwardPost createPost(
            String serial,
            String sourceChatId,
            Integer sourceMessageId,
            String sourceMediaGroupId,
            String originalText,
            String processedText,
            String outputText,
            List<ForwardPostMediaItem> mediaItems
    );

    /**
     * 依據貼文 ID 查詢貼文。
     *
     * @param postId 貼文 ID
     * @return 貼文
     */
    Optional<ForwardPost> findById(String postId);

    /**
     * 依據貼文 ID 查詢媒體列表。
     *
     * @param postId 貼文 ID
     * @return 媒體列表
     */
    List<ForwardPostMedia> findMediaByPostId(String postId);

    /**
     * 依建立時間取得全部貼文（升冪）。
     *
     * @return 貼文列表
     */
    List<ForwardPost> findAllOrderByCreatedAtAsc();
}
