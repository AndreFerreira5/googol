package com.googol.backend.strategy;

import com.googol.backend.model.RawUrl;

import java.util.concurrent.LinkedBlockingDeque; /**
 * DFS startegy.
 */
public class DFSStartegy implements CrawlingStrategy{
    public void addUrl(LinkedBlockingDeque<RawUrl> deque, RawUrl url){
        deque.addFirst(url);
    }
    public String getStrategyName(){
        return "Depth First Search";
    }
}
