package io.github.yienruuuuu.bean.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * @author Eric.Lee
 * Date: 2026/01/27
 */
@Getter
@Setter
@Entity
@Table(name = "config", schema = "tg_manager_bot")
public class ConfigSetting extends BaseEntity {
    @Id
    @Column(name = "config_type", nullable = false, length = 64)
    private String configType;

    @Column(name = "value", nullable = false, length = 256)
    private String value;
}
