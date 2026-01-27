package io.github.yienruuuuu.repository;

import io.github.yienruuuuu.bean.entity.ConfigSetting;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * @author Eric.Lee
 * Date: 2026/01/27
 */
public interface ConfigRepository extends JpaRepository<ConfigSetting, String> {
    /**
     * 依據設定類型取得設定。
     *
     * @param configType 設定類型
     * @return 設定
     */
    ConfigSetting findByConfigType(String configType);
}
