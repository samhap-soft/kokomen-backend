package com.samhap.kokomen.recruit.schedular.service;

import lombok.Getter;

@Getter
public class PaginationState {

    private int currentPage = 0;
    private boolean hasMore = true;

    public boolean hasMore() {
        return hasMore;
    }

    public void nextPage() {
        currentPage++;
    }

    public void stop() {
        hasMore = false;
    }
}
