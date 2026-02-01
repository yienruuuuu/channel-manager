package io.github.yienruuuuu.service.business;

import io.github.yienruuuuu.bean.entity.SubForwardPost;
import io.github.yienruuuuu.bean.entity.SubForwardPostMedia;
import io.github.yienruuuuu.service.business.model.ForwardPostMediaItem;

import java.util.List;
import java.util.Optional;

/**
 * @author Eric.Lee
 * Date: 2026/02/01
 */
public interface SubForwardPostService {
    /**
     * 建立貼文與媒體記錄。
     *
     * @param serial 序號
     * @param sourceChatId 來源聊天 ID
     * @param sourceMessageId 來源訊息 ID
     * @param sourceMediaGroupId 來源 media group ID
     * @param forwardFromChatId 轉傳來源 chat ID
     * @param forwardFromChatTitle 轉傳來源 chat 標題
     * @param forwardFromUserId 轉傳來源 user/bot ID
     * @param forwardFromUserUsername 轉傳來源 user/bot username
     * @param forwardFromUserName 轉傳來源 user/bot 顯示名稱
     * @param originalText 原始文字
     * @param processedText 處理後文字
     * @param outputText 最終輸出文字
     * @param mediaItems 媒體項目
     * @return 建立完成的貼文
     */
    SubForwardPost createPost(
            String serial,
            String sourceChatId,
            Integer sourceMessageId,
            String sourceMediaGroupId,
            String forwardFromChatId,
            String forwardFromChatTitle,
            String forwardFromUserId,
            String forwardFromUserUsername,
            String forwardFromUserName,
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
    Optional<SubForwardPost> findById(String postId);

    /**
     * 依據貼文 ID 查詢媒體列表。
     *
     * @param postId 貼文 ID
     * @return 媒體列表
     */
    List<SubForwardPostMedia> findMediaByPostId(String postId);

    /**
     * 依建立時間取得全部貼文（升冪）。
     *
     * @return 貼文列表
     */
    List<SubForwardPost> findAllOrderByCreatedAtAsc();

    /**
     * 取得指定前綴最新的序號。
     *
     * @param serialPrefix 序號前綴 (yyyy-MM-dd_)
     * @return 序號字串，若無資料回傳 null
     */
    String findLatestSerialByPrefix(String serialPrefix);

    /**
     * 判斷媒體檔案是否已存在。
     *
     * @param fileId 檔案 ID
     * @return true 表示已存在
     */
    boolean existsByMediaFileId(String fileId);
}
