package com.example.cvhelper.controller;

import com.example.cvhelper.service.HHService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

import jakarta.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/api/hh")
public class HHAuthController {
    private static final Logger logger = LoggerFactory.getLogger(HHAuthController.class);
    
    @Autowired
    private HHService hhService;

    @GetMapping("/auth")
    @ResponseBody
    public ResponseEntity<Map<String, String>> startAuth() {
        String authUrl = hhService.getAuthUrl();
        logger.info("Generated auth URL: {}", authUrl);
        Map<String, String> response = new HashMap<>();
        response.put("authUrl", authUrl);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/callback")
    public RedirectView handleCallback(
            @RequestParam("code") String code,
            @RequestParam(value = "error", required = false) String error,
            @RequestParam(value = "state", required = false) String state,
            HttpSession session
    ) {
        logger.info("Received callback - code: {}, error: {}, state: {}", 
                   code != null ? code.substring(0, Math.min(code.length(), 10)) + "..." : "null", 
                   error, state);
        
        if (error != null) {
            logger.error("Authorization error: {}", error);
            // В случае ошибки редиректим на главную страницу с параметром ошибки
            RedirectView redirectView = new RedirectView("/?auth_error=" + error);
            redirectView.setStatusCode(HttpStatus.FOUND);
            return redirectView;
        }

        try {
            logger.info("Exchanging authorization code for token...");
            Map<String, Object> tokenData = hhService.getAccessToken(code);
            logger.info("Successfully received token data with keys: {}", tokenData.keySet());
            
            // Сохраняем полные данные токена в сессии
            session.setAttribute("hh_token_data", tokenData);
            session.setAttribute("hh_access_token", tokenData.get("access_token"));
            
            logger.info("Token saved to session, redirecting to success page");
            
            // Успешная авторизация - редиректим на главную страницу с параметром успеха
            RedirectView redirectView = new RedirectView("/?auth_success=true");
            redirectView.setStatusCode(HttpStatus.FOUND);
            return redirectView;
        } catch (Exception e) {
            logger.error("Error getting access token", e);
            // В случае ошибки получения токена редиректим на главную страницу с параметром ошибки
            RedirectView redirectView = new RedirectView("/?auth_error=token_error&message=" + e.getMessage());
            redirectView.setStatusCode(HttpStatus.FOUND);
            return redirectView;
        }
    }

    @GetMapping("/refresh")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> refreshToken(HttpSession session) {
        try {
            Map<String, Object> tokenData = (Map<String, Object>) session.getAttribute("hh_token_data");
            
            if (tokenData == null || !tokenData.containsKey("refresh_token")) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "Refresh token не найден. Необходима повторная авторизация.");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
            }
            
            String refreshToken = (String) tokenData.get("refresh_token");
            Map<String, Object> newTokenData = hhService.refreshAccessToken(refreshToken);
            
            // Обновляем данные токена в сессии
            session.setAttribute("hh_token_data", newTokenData);
            session.setAttribute("hh_access_token", newTokenData.get("access_token"));
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Токен успешно обновлен");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Ошибка обновления токена: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    @GetMapping("/vacancy")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getVacancyInfo(
            @RequestParam("vacancyUrl") String vacancyUrl,
            HttpSession session
    ) {
        try {
            Map<String, Object> tokenData = (Map<String, Object>) session.getAttribute("hh_token_data");
            String accessToken = (String) session.getAttribute("hh_access_token");
            
            // Проверяем, не истек ли токен
            if (tokenData != null && hhService.isTokenExpired(tokenData)) {
                // Пытаемся обновить токен
                try {
                    String refreshToken = (String) tokenData.get("refresh_token");
                    if (refreshToken != null) {
                        Map<String, Object> newTokenData = hhService.refreshAccessToken(refreshToken);
                        session.setAttribute("hh_token_data", newTokenData);
                        accessToken = (String) newTokenData.get("access_token");
                        session.setAttribute("hh_access_token", accessToken);
                    }
                } catch (Exception e) {
                    Map<String, Object> error = new HashMap<>();
                    error.put("error", "Токен истек и не может быть обновлен. Необходима повторная авторизация.");
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
                }
            }
            
            String vacancyId = hhService.extractVacancyId(vacancyUrl);
            Map<String, Object> vacancyInfo = hhService.getVacancyInfo(vacancyId, accessToken);
            
            return ResponseEntity.ok(vacancyInfo);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Ошибка получения информации о вакансии: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
} 