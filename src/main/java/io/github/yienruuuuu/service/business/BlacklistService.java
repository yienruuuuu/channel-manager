package io.github.yienruuuuu.service.business;

/**
 * @author Eric.Lee
 * Date: 2026/01/23
 */
public interface BlacklistService {
    /**
     * 移除黑名單字串。
     *
     * @param input 原始文字
     * @return 移除黑名單後的文字
     */
    String filter(String input);
}
