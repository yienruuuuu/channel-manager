package io.github.yienruuuuu.controller;

import io.github.yienruuuuu.repository.ForwardPostRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Eric.Lee
 * Date: 2026/01/27
 */
@RestController
@RequestMapping("/api/admin")
public class HashtagController {
    private final ForwardPostRepository forwardPostRepository;

    public HashtagController(ForwardPostRepository forwardPostRepository) {
        this.forwardPostRepository = forwardPostRepository;
    }

    @GetMapping("/hashtags")
    public List<String> listHashtags() {
        List<String> outputs = forwardPostRepository.findAllOutputText();
        Set<String> result = new LinkedHashSet<>();
        for (String output : outputs) {
            if (output == null || output.isBlank()) {
                continue;
            }
            extractHashtags(output, result);
        }
        return new ArrayList<>(result);
    }

    private void extractHashtags(String text, Set<String> target) {
        String[] tokens = text.split("\\s+");
        for (String token : tokens) {
            if (token.startsWith("#") && token.length() > 1) {
                target.add(token);
            }
        }
    }
}
