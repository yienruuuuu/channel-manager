package io.github.yienruuuuu.service.business;

/**
 * @author Eric.Lee
 * Date: 2026/01/27
 */
public interface ConfigService {
    /**
     * 取得設定值。
     *
     * @param configType 設定類型
     * @return 設定值，找不到回傳 null
     */
    String getValue(String configType);

    /**
     * 取得整數設定值。
     *
     * @param configType 設定類型
     * @param defaultValue 預設值
     * @return 轉換後的整數，解析失敗回傳預設值
     */
    int getIntValue(String configType, int defaultValue);
}
