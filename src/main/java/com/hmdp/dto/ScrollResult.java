package com.hmdp.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ScrollResult {
    private List<?> list;
    private Double lastScore;
    private Long lastId;
    private Boolean hasMore;

    public ScrollResult() {
        this.list = new ArrayList<>();
        this.lastScore = null;
        this.lastId = null;
        this.hasMore = false;
    }

    public ScrollResult(List<?> list, Double lastScore, Long lastId, Boolean hasMore) {
        this.list = list;
        this.lastScore = lastScore;
        this.lastId = lastId;
        this.hasMore = hasMore;
    }
}
