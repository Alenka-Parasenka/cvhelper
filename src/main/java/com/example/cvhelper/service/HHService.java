package com.example.cvhelper.service;

import com.example.cvhelper.config.HHConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.Map;

@Service
public class HHService {
    private static final Logger logger = LoggerFactory.getLogger(HHService.class);
    
    @Autowired
    private HHConfig hhConfig;
    
    private final RestTemplate restTemplate = new RestTemplate();

    public String getAuthUrl() {
        String authUrl = UriComponentsBuilder.fromHttpUrl(hhConfig.getAuthUrl())
                .queryParam("response_type", "code")
                .queryParam("client_id", hhConfig.getClientId())
                .queryParam("redirect_uri", hhConfig.getRedirectUri())
                .build()
                .toUriString();
        
        logger.info("Generated auth URL: {}", authUrl);
        return authUrl;
    }

    public Map<String, Object> getAccessToken(String code) {
        logger.info("Getting access token for code: {}...", code.substring(0, Math.min(code.length(), 10)) + "...");
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.set("User-Agent", "CVHelper/1.0 (cvhelper-feedback@gmail.com)");

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "authorization_code");
        body.add("client_id", hhConfig.getClientId());
        body.add("client_secret", hhConfig.getClientSecret());
        body.add("code", code);
        body.add("redirect_uri", hhConfig.getRedirectUri());

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        logger.info("Making token request to: {}", hhConfig.getTokenUrl());
        ResponseEntity<Map> response = restTemplate.postForEntity(hhConfig.getTokenUrl(), request, Map.class);
        
        logger.info("Token response status: {}", response.getStatusCode());
        
        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
            Map<String, Object> tokenData = response.getBody();
            logger.info("Token response contains keys: {}", tokenData.keySet());
            
            // Добавляем время получения токена для отслеживания истечения
            tokenData.put("token_created_at", Instant.now().getEpochSecond());
            
            return tokenData;
        }
        
        // Обработка ошибок
        if (response.getBody() != null && response.getBody().containsKey("error")) {
            String error = (String) response.getBody().get("error");
            String errorDescription = (String) response.getBody().get("error_description");
            logger.error("Token error: {} - {}", error, errorDescription);
            throw new RuntimeException("Ошибка получения токена: " + error + " - " + errorDescription);
        }
        
        logger.error("Unexpected token response: {}", response.getBody());
        throw new RuntimeException("Ошибка получения access_token: HTTP " + response.getStatusCode());
    }

    public Map<String, Object> refreshAccessToken(String refreshToken) {
        logger.info("Refreshing access token...");
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.set("User-Agent", "CVHelper/1.0 (cvhelper-feedback@gmail.com)");

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "refresh_token");
        body.add("refresh_token", refreshToken);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(hhConfig.getTokenUrl(), request, Map.class);
        
        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
            Map<String, Object> tokenData = response.getBody();
            
            // Добавляем время получения токена для отслеживания истечения
            tokenData.put("token_created_at", Instant.now().getEpochSecond());
            
            return tokenData;
        }
        
        // Обработка ошибок
        if (response.getBody() != null && response.getBody().containsKey("error")) {
            String error = (String) response.getBody().get("error");
            String errorDescription = (String) response.getBody().get("error_description");
            throw new RuntimeException("Ошибка обновления токена: " + error + " - " + errorDescription);
        }
        
        throw new RuntimeException("Ошибка обновления access_token: HTTP " + response.getStatusCode());
    }

    public boolean isTokenExpired(Map<String, Object> tokenData) {
        if (tokenData == null || !tokenData.containsKey("expires_in") || !tokenData.containsKey("token_created_at")) {
            return true;
        }
        
        Long expiresIn = ((Number) tokenData.get("expires_in")).longValue();
        Long createdAt = ((Number) tokenData.get("token_created_at")).longValue();
        Long currentTime = Instant.now().getEpochSecond();
        
        // Токен считается истекшим, если до истечения осталось менее 5 минут
        return (currentTime - createdAt) >= (expiresIn - 300);
    }

    public Map<String, Object> getVacancyInfo(String vacancyId, String accessToken) {
        String url = hhConfig.getApiBaseUrl() + "/vacancies/" + vacancyId;
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "CVHelper/1.0 (cvhelper-feedback@gmail.com)");
        
        if (accessToken != null && !accessToken.isEmpty()) {
            headers.setBearerAuth(accessToken);
        }
        
        HttpEntity<String> request = new HttpEntity<>(headers);
        
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
        
        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
            return response.getBody();
        }
        
        throw new RuntimeException("Ошибка получения информации о вакансии");
    }

    public String extractVacancyId(String vacancyUrl) {
        // Извлекаем ID вакансии из URL вида https://hh.ru/vacancy/12345678
        String[] parts = vacancyUrl.split("/");
        return parts[parts.length - 1];
    }
} 