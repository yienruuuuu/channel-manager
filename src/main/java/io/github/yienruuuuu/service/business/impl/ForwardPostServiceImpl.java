package io.github.yienruuuuu.service.business.impl;

import io.github.yienruuuuu.bean.entity.ForwardPost;
import io.github.yienruuuuu.bean.entity.ForwardPostMedia;
import io.github.yienruuuuu.repository.ForwardPostMediaRepository;
import io.github.yienruuuuu.repository.ForwardPostRepository;
import io.github.yienruuuuu.service.business.ForwardPostService;
import io.github.yienruuuuu.service.business.model.ForwardPostMediaItem;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * @author Eric.Lee
 * Date: 2026/01/23
 */
@Service("forwardPostService")
public class ForwardPostServiceImpl implements ForwardPostService {
    private final ForwardPostRepository forwardPostRepository;
    private final ForwardPostMediaRepository forwardPostMediaRepository;

    /**
     * 建立貼文服務。
     *
     * @param forwardPostRepository 貼文資料存取物件
     * @param forwardPostMediaRepository 媒體資料存取物件
     */
    public ForwardPostServiceImpl(ForwardPostRepository forwardPostRepository, ForwardPostMediaRepository forwardPostMediaRepository) {
        this.forwardPostRepository = forwardPostRepository;
        this.forwardPostMediaRepository = forwardPostMediaRepository;
    }

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
    @Override
    public ForwardPost createPost(
            String serial,
            String sourceChatId,
            Integer sourceMessageId,
            String sourceMediaGroupId,
            String forwardFromChatId,
            String forwardFromChatTitle,
            String originalText,
            String processedText,
            String outputText,
            List<ForwardPostMediaItem> mediaItems
    ) {
        ForwardPost post = new ForwardPost();
        post.setId(UUID.randomUUID().toString());
        post.setSerial(serial);
        post.setSourceChatId(sourceChatId);
        post.setSourceMessageId(sourceMessageId);
        post.setSourceMediaGroupId(sourceMediaGroupId);
        post.setForwardFromChatId(forwardFromChatId);
        post.setForwardFromChatTitle(forwardFromChatTitle);
        post.setOriginalText(originalText);
        post.setProcessedText(processedText);
        post.setOutputText(outputText);
        forwardPostRepository.save(post);

        if (mediaItems != null) {
            int index = 0;
            for (ForwardPostMediaItem item : mediaItems) {
                ForwardPostMedia media = new ForwardPostMedia();
                media.setPostId(post.getId());
                media.setMediaType(item.getMediaType());
                media.setFileId(item.getFileId());
                media.setSortOrder(index++);
                forwardPostMediaRepository.save(media);
            }
        }
        return post;
    }

    /**
     * 依據貼文 ID 查詢貼文。
     *
     * @param postId 貼文 ID
     * @return 貼文
     */
    @Override
    public Optional<ForwardPost> findById(String postId) {
        return forwardPostRepository.findById(postId);
    }

    /**
     * 依據貼文 ID 查詢媒體列表。
     *
     * @param postId 貼文 ID
     * @return 媒體列表
     */
    @Override
    public List<ForwardPostMedia> findMediaByPostId(String postId) {
        return forwardPostMediaRepository.findByPostIdOrderBySortOrderAsc(postId);
    }

    /**
     * 依建立時間升冪取得所有貼文。
     *
     * @return 貼文列表
     */
    @Override
    public List<ForwardPost> findAllOrderByCreatedAtAsc() {
        return forwardPostRepository.findAllByOrderByCreatedAtAsc();
    }

    @Override
    public boolean existsByMediaFileId(String fileId) {
        if (fileId == null || fileId.isBlank()) {
            return false;
        }
        return forwardPostMediaRepository.existsByFileId(fileId);
    }

    @Override
    public String findLatestSerialByPrefix(String serialPrefix) {
        if (serialPrefix == null || serialPrefix.isBlank()) {
            return null;
        }
        ForwardPost post = forwardPostRepository.findTopBySerialStartingWithOrderBySerialDesc(serialPrefix);
        return post == null ? null : post.getSerial();
    }
}
