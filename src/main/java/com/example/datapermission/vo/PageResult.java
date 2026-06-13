package com.example.datapermission.vo;

import lombok.Data;
import java.util.List;

@Data
public class PageResult<T> {
    private Long total;
    private Long pageNum;
    private Long pageSize;
    private Long pages;
    private List<T> list;

    public PageResult() {
    }

    public PageResult(Long total, Long pageNum, Long pageSize, List<T> list) {
        this.total = total;
        this.pageNum = pageNum;
        this.pageSize = pageSize;
        this.pages = (total + pageSize - 1) / pageSize;
        this.list = list;
    }
}
