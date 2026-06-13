package com.example.datapermission.dto;

import lombok.Data;
import java.util.List;

@Data
public class AlertHandleRequest {

    private String action;

    private String handleResult;

    private List<String> restrictActions;

    private String nextReviewDate;
}
