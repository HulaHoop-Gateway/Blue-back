package com.hulahoop.blueback.ai.utils;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

public class HttpClient {

    private static final RestTemplate restTemplate = new RestTemplate();

    public static void post(String url, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        restTemplate.postForObject(url, request, Void.class);
    }
}
