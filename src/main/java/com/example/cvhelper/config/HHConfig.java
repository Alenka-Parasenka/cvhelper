package com.example.cvhelper.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HHConfig {
    @Value("${hh.client.id}")
    private String clientId;
    
    @Value("${hh.client.secret}")
    private String clientSecret;
    
    @Value("${hh.redirect.uri}")
    private String redirectUri;
    
    @Value("${hh.api.base.url:https://api.hh.ru}")
    private String apiBaseUrl;
    
    @Value("${hh.auth.url:https://hh.ru/oauth/authorize}")
    private String authUrl;
    
    @Value("${hh.token.url:https://api.hh.ru/token}")
    private String tokenUrl;

    public String getClientId() {
        return clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    public String getApiBaseUrl() {
        return apiBaseUrl;
    }

    public String getAuthUrl() {
        return authUrl;
    }

    public String getTokenUrl() {
        return tokenUrl;
    }
} 