package io.github.yienruuuuu.service.business.impl;

import io.github.yienruuuuu.bean.entity.SubForwardPost;
import io.github.yienruuuuu.bean.entity.SubForwardPostMedia;
import io.github.yienruuuuu.repository.SubForwardPostMediaRepository;
import io.github.yienruuuuu.repository.SubForwardPostRepository;
import io.github.yienruuuuu.service.business.SubForwardPostService;
import io.github.yienruuuuu.service.business.model.ForwardPostMediaItem;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * @author Eric.Lee
 * Date: 2026/02/01
 */
@Service("subForwardPostService")
public class SubForwardPostServiceImpl implements SubForwardPostService {
    private final SubForwardPostRepository subForwardPostRepository;
    private final SubForwardPostMediaRepository subForwardPostMediaRepository;

    public SubForwardPostServiceImpl(
            SubForwardPostRepository subForwardPostRepository,
            SubForwardPostMediaRepository subForwardPostMediaRepository
    ) {
        this.subForwardPostRepository = subForwardPostRepository;
        this.subForwardPostMediaRepository = subForwardPostMediaRepository;
    }

    @Override
    public SubForwardPost createPost(
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
    ) {
        SubForwardPost post = new SubForwardPost();
        post.setId(UUID.randomUUID().toString());
        post.setSerial(serial);
        post.setSourceChatId(sourceChatId);
        post.setSourceMessageId(sourceMessageId);
        post.setSourceMediaGroupId(sourceMediaGroupId);
        post.setForwardFromChatId(forwardFromChatId);
        post.setForwardFromChatTitle(forwardFromChatTitle);
        post.setForwardFromUserId(forwardFromUserId);
        post.setForwardFromUserUsername(forwardFromUserUsername);
        post.setForwardFromUserName(forwardFromUserName);
        post.setOriginalText(originalText);
        post.setProcessedText(processedText);
        post.setOutputText(outputText);
        subForwardPostRepository.save(post);

        if (mediaItems != null) {
            int index = 0;
            for (ForwardPostMediaItem item : mediaItems) {
                SubForwardPostMedia media = new SubForwardPostMedia();
                media.setPostId(post.getId());
                media.setMediaType(item.getMediaType());
                media.setFileId(item.getFileId());
                media.setSortOrder(index++);
                subForwardPostMediaRepository.save(media);
            }
        }
        return post;
    }

    @Override
    public Optional<SubForwardPost> findById(String postId) {
        return subForwardPostRepository.findById(postId);
    }

    @Override
    public List<SubForwardPostMedia> findMediaByPostId(String postId) {
        return subForwardPostMediaRepository.findByPostIdOrderBySortOrderAsc(postId);
    }

    @Override
    public List<SubForwardPost> findAllOrderByCreatedAtAsc() {
        return subForwardPostRepository.findAllByOrderByCreatedAtAsc();
    }

    @Override
    public boolean existsByMediaFileId(String fileId) {
        if (fileId == null || fileId.isBlank()) {
            return false;
        }
        return subForwardPostMediaRepository.existsByFileId(fileId);
    }

    @Override
    public String findLatestSerialByPrefix(String serialPrefix) {
        if (serialPrefix == null || serialPrefix.isBlank()) {
            return null;
        }
        SubForwardPost post = subForwardPostRepository.findTopBySerialStartingWithOrderBySerialDesc(serialPrefix);
        return post == null ? null : post.getSerial();
    }
}
