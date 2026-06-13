package com.example.datapermission.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DesensitizationUtil {

    @Value("${data-permission.desensitization.default-mask-char:*}")
    private String defaultMaskChar;

    public String desensitize(String value, String type, String pattern) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        if ("HIDE".equals(type)) {
            return null;
        }

        if ("MASK".equals(type)) {
            return maskValue(value, pattern);
        }

        if ("HASH".equals(type)) {
            return hashValue(value);
        }

        return value;
    }

    private String maskValue(String value, String pattern) {
        if (pattern == null || pattern.isEmpty()) {
            pattern = "前1后1";
        }

        int length = value.length();
        if (length <= 2) {
            return repeatMask(length);
        }

        if ("前3后4".equals(pattern)) {
            if (length <= 7) {
                return repeatMask(length);
            }
            return value.substring(0, 3) + repeatMask(length - 7) + value.substring(length - 4);
        }

        if ("前2后@".equals(pattern)) {
            int atIndex = value.indexOf('@');
            if (atIndex > 0) {
                String prefix = value.substring(0, 2);
                String domain = value.substring(atIndex);
                return prefix + repeatMask(atIndex - 2) + domain;
            }
            return value.substring(0, 2) + repeatMask(length - 4) + value.substring(length - 2);
        }

        if ("前4后2".equals(pattern)) {
            if (length <= 6) {
                return repeatMask(length);
            }
            return value.substring(0, 4) + repeatMask(length - 6) + value.substring(length - 2);
        }

        if ("只显示万".equals(pattern)) {
            try {
                double num = Double.parseDouble(value);
                return String.format("%.1f万", num / 10000);
            } catch (NumberFormatException e) {
                return repeatMask(length);
            }
        }

        if ("只显示省市".equals(pattern)) {
            String[] parts = value.split("[省市区县]");
            if (parts.length > 0) {
                return parts[0] + "***";
            }
            return value.substring(0, Math.min(4, length)) + "***";
        }

        String[] parts = pattern.split("后");
        if (parts.length == 2) {
            try {
                int front = Integer.parseInt(parts[0].replace("前", ""));
                int back = Integer.parseInt(parts[1]);
                if (length <= front + back) {
                    return repeatMask(length);
                }
                return value.substring(0, front) + repeatMask(length - front - back) + value.substring(length - back);
            } catch (NumberFormatException e) {
                return value;
            }
        }

        return value.substring(0, 1) + repeatMask(length - 1);
    }

    private String hashValue(String value) {
        return String.valueOf(value.hashCode());
    }

    private String repeatMask(int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(defaultMaskChar);
        }
        return sb.toString();
    }
}
