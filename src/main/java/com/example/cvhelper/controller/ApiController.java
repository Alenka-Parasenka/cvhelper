package com.example.cvhelper.controller;

import com.example.cvhelper.service.FileParseService;
import com.example.cvhelper.service.HHService;
import com.example.cvhelper.service.OpenAIService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ApiController {
    @Autowired
    private FileParseService fileParseService;
    @Autowired
    private HHService hhService;
    @Autowired
    private OpenAIService openAIService;

    @PostMapping(value = "/parse-resume", consumes = { MediaType.MULTIPART_FORM_DATA_VALUE })
    public Map<String, String> parseResume(
            @RequestParam(value = "resumeText", required = false) String resumeText,
            @RequestParam(value = "resumeFile", required = false) MultipartFile resumeFile,
            @RequestParam("vacancyUrl") String vacancyUrl,
            HttpSession session
    ) throws IOException {
        String text = resumeText;
        if ((text == null || text.isBlank()) && resumeFile != null && !resumeFile.isEmpty()) {
            text = fileParseService.parseFile(resumeFile.getInputStream());
        }
        
        // Сохраняем данные в сессии для восстановления после авторизации
        session.setAttribute("pending_resume_text", text);
        session.setAttribute("pending_vacancy_url", vacancyUrl);
        if (resumeFile != null && !resumeFile.isEmpty()) {
            session.setAttribute("pending_resume_filename", resumeFile.getOriginalFilename());
        }
        
        Map<String, String> result = new HashMap<>();
        result.put("parsedResume", text != null ? text : "");
        result.put("vacancyUrl", vacancyUrl);
        return result;
    }

    @PostMapping(value = "/generate", consumes = { MediaType.MULTIPART_FORM_DATA_VALUE })
    public Map<String, String> generate(
            @RequestParam(value = "resumeText", required = false) String resumeText,
            @RequestParam(value = "resumeFile", required = false) MultipartFile resumeFile,
            @RequestParam("vacancyUrl") String vacancyUrl,
            HttpSession session
    ) throws IOException {
        String text = resumeText;
        if ((text == null || text.isBlank()) && resumeFile != null && !resumeFile.isEmpty()) {
            text = fileParseService.parseFile(resumeFile.getInputStream());
        }
        
        // Получаем данные токена и проверяем его актуальность
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
                Map<String, String> error = new HashMap<>();
                error.put("error", "Токен истек и не может быть обновлен. Необходима повторная авторизация.");
                return error;
            }
        }
        
        String vacancyId = hhService.extractVacancyId(vacancyUrl);
        Map<String, Object> vacancyInfo = hhService.getVacancyInfo(vacancyId, accessToken);
        Map<String, String> resumeResult = openAIService.generateCustomizedResume(text, vacancyInfo);
        Map<String, String> coverLetterResult = openAIService.generateCoverLetter(text, vacancyInfo);
        Map<String, String> result = new HashMap<>();
        result.putAll(resumeResult);
        result.putAll(coverLetterResult);
        return result;
    }

    @GetMapping("/restore-session")
    public Map<String, Object> restoreSession(HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        
        String resumeText = (String) session.getAttribute("pending_resume_text");
        String vacancyUrl = (String) session.getAttribute("pending_vacancy_url");
        String resumeFilename = (String) session.getAttribute("pending_resume_filename");
        
        if (resumeText != null && vacancyUrl != null) {
            result.put("resumeText", resumeText);
            result.put("vacancyUrl", vacancyUrl);
            if (resumeFilename != null) {
                result.put("resumeFilename", resumeFilename);
            }
            
            // Очищаем данные из сессии
            session.removeAttribute("pending_resume_text");
            session.removeAttribute("pending_vacancy_url");
            session.removeAttribute("pending_resume_filename");
            
            // Автоматически генерируем резюме и сопроводительное письмо
            try {
                // Получаем данные токена и проверяем его актуальность
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
                        result.put("error", "Токен истек и не может быть обновлен. Необходима повторная авторизация.");
                        return result;
                    }
                }
                
                String vacancyId = hhService.extractVacancyId(vacancyUrl);
                Map<String, Object> vacancyInfo = hhService.getVacancyInfo(vacancyId, accessToken);
                Map<String, String> resumeResult = openAIService.generateCustomizedResume(resumeText, vacancyInfo);
                Map<String, String> coverLetterResult = openAIService.generateCoverLetter(resumeText, vacancyInfo);
                
                result.putAll(resumeResult);
                result.putAll(coverLetterResult);
                result.put("autoGenerated", true);
            } catch (Exception e) {
                result.put("error", "Ошибка автоматической генерации: " + e.getMessage());
            }
        } else {
            result.put("error", "Нет сохраненных данных для восстановления");
        }
        
        return result;
    }
} 