package io.github.yienruuuuu.bean.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * @author Eric.Lee
 * Date: 2026/02/01
 */
@Getter
@Setter
@Entity
@Table(name = "sub_forward_post_media", schema = "tg_manager_bot")
public class SubForwardPostMedia extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "post_id", nullable = false, length = 36)
    private String postId;

    @Column(name = "media_type", nullable = false, length = 32)
    private String mediaType;

    @Column(name = "file_id", nullable = false, length = 512)
    private String fileId;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;
}
