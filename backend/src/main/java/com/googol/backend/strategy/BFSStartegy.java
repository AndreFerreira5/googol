package com.googol.backend.strategy;

import com.googol.backend.model.RawUrl;

import java.util.concurrent.LinkedBlockingDeque; /**
 * BFS startegy.
 */
public class BFSStartegy implements CrawlingStrategy{
    public void addUrl(LinkedBlockingDeque<RawUrl> deque, RawUrl url){
        deque.addLast(url);
    }
    public String getStrategyName(){
        return "Bread First Search";
    }
}
