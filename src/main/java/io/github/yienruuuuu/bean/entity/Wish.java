package io.github.yienruuuuu.bean.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * @author Eric.Lee
 * Date: 2026/02/02
 */
@Getter
@Setter
@Entity
@Table(name = "wish", schema = "tg_manager_bot")
public class Wish extends BaseEntity {
    @Id
    @Column(name = "id", nullable = false, length = 36)
    private String id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "platform", nullable = false, length = 128)
    private String platform;

    @Column(name = "person_name", nullable = false, length = 128)
    private String personName;

    @Column(name = "wish_type", nullable = false, length = 128)
    private String wishType;

    @Column(name = "other_text", nullable = false, length = 100)
    private String otherText;

    @Column(name = "ins_dat", nullable = false)
    private Instant insDat;
}
