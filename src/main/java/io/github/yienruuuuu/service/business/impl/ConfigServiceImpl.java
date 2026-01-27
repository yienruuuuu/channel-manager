package io.github.yienruuuuu.service.business.impl;

import io.github.yienruuuuu.bean.entity.ConfigSetting;
import io.github.yienruuuuu.repository.ConfigRepository;
import io.github.yienruuuuu.service.business.ConfigService;
import org.springframework.stereotype.Service;

/**
 * @author Eric.Lee
 * Date: 2026/01/27
 */
@Service("configService")
public class ConfigServiceImpl implements ConfigService {
    private final ConfigRepository configRepository;

    public ConfigServiceImpl(ConfigRepository configRepository) {
        this.configRepository = configRepository;
    }

    @Override
    public String getValue(String configType) {
        if (configType == null || configType.isBlank()) {
            return null;
        }
        ConfigSetting setting = configRepository.findByConfigType(configType);
        return setting == null ? null : setting.getValue();
    }

    @Override
    public int getIntValue(String configType, int defaultValue) {
        String value = getValue(configType);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
