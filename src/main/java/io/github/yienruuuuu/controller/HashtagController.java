package io.github.yienruuuuu.controller;

import io.github.yienruuuuu.repository.ForwardPostRepository;
import io.github.yienruuuuu.repository.SubForwardPostRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Eric.Lee
 * Date: 2026/01/27
 */
@RestController
@RequestMapping("/api/admin")
public class HashtagController {
    private final ForwardPostRepository forwardPostRepository;
    private final SubForwardPostRepository subForwardPostRepository;

    public HashtagController(ForwardPostRepository forwardPostRepository, SubForwardPostRepository subForwardPostRepository) {
        this.forwardPostRepository = forwardPostRepository;
        this.subForwardPostRepository = subForwardPostRepository;
    }

    @GetMapping("/hashtags")
    public HashtagResponse listHashtags() {
        return buildHashtagResponse(forwardPostRepository.findAllOutputText());
    }

    @GetMapping("/sub-hashtags")
    public HashtagResponse listSubHashtags() {
        return buildHashtagResponse(subForwardPostRepository.findAllOutputText());
    }

    private HashtagResponse buildHashtagResponse(List<String> outputs) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String output : outputs) {
            if (output == null || output.isBlank()) {
                continue;
            }
            extractHashtags(output, counts);
        }
        Map<String, Integer> sorted = counts.entrySet().stream()
                .filter(entry -> entry.getValue() > 5)
                .sorted(java.util.Comparator
                        .comparingInt((Map.Entry<String, Integer> e) -> e.getKey().length())
                        .thenComparing(Map.Entry::getKey))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> a,
                        LinkedHashMap::new
        ));
        List<String> tags = new ArrayList<>(sorted.keySet());
        return new HashtagResponse(sorted, tags);
    }

    private void extractHashtags(String text, Map<String, Integer> target) {
        String[] tokens = text.split("\\s+");
        for (String token : tokens) {
            if (token.startsWith("#") && token.length() > 1) {
                target.merge(token, 1, Integer::sum);
            }
        }
    }

    public static class HashtagResponse {
        public Map<String, Integer> counts;
        public List<String> hashtags;

        public HashtagResponse(Map<String, Integer> counts, List<String> hashtags) {
            this.counts = counts;
            this.hashtags = hashtags;
        }
    }
}
