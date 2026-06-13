package com.example.datapermission.util;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

public class JsonUtil {

    public static String toJsonString(Object obj) {
        return JSON.toJSONString(obj);
    }

    public static <T> T parseObject(String json, Class<T> clazz) {
        return JSON.parseObject(json, clazz);
    }

    public static JSONObject parseObject(String json) {
        return JSON.parseObject(json);
    }

    public static <T> T parseArray(String json, Class<T> clazz) {
        return JSON.parseObject(json, clazz);
    }
}
