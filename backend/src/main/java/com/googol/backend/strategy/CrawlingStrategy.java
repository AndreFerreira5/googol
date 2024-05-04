package com.googol.backend.strategy;

import java.util.concurrent.LinkedBlockingDeque;
import com.googol.backend.model.RawUrl;

/**
 * Crawling Strategy interface.
 * Used to create different crawling strategies
 */
public interface CrawlingStrategy {
    /**
     * Add url to deque
     *
     * @param deque the deque
     * @param url   the url
     */
    void addUrl(LinkedBlockingDeque<RawUrl> deque, RawUrl url);
    String getStrategyName();
}

