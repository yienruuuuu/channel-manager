package io.github.yienruuuuu.service.business.impl;

import io.github.yienruuuuu.bean.entity.BlacklistTerm;
import io.github.yienruuuuu.repository.BlacklistTermRepository;
import io.github.yienruuuuu.service.business.BlacklistService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author Eric.Lee
 * Date: 2026/01/23
 */
@Service("blacklistService")
public class BlacklistServiceImpl implements BlacklistService {
    private final BlacklistTermRepository blacklistTermRepository;

    /**
     * 建立黑名單服務。
     *
     * @param blacklistTermRepository 黑名單資料存取物件
     */
    public BlacklistServiceImpl(BlacklistTermRepository blacklistTermRepository) {
        this.blacklistTermRepository = blacklistTermRepository;
    }

    /**
     * 依照資料庫黑名單字串逐一移除。
     *
     * @param input 原始文字
     * @return 移除黑名單後的文字
     */
    @Override
    public String filter(String input) {
        if (input == null || input.isBlank()) {
            return input;
        }
        List<BlacklistTerm> terms = blacklistTermRepository.findAll();
        String result = input;
        for (BlacklistTerm term : terms) {
            if (term.getTerm() == null || term.getTerm().isBlank()) {
                continue;
            }
            result = result.replace(term.getTerm(), "");
        }
        return result;
    }
}
