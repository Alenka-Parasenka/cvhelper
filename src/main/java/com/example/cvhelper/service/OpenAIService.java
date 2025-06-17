package com.example.cvhelper.service;

import com.example.cvhelper.config.OpenAIConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class OpenAIService {
    @Autowired
    private OpenAIConfig openAIConfig;
    
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Map<String, String> generateCustomizedResume(String resumeText, Map<String, Object> vacancyInfo) {
        String prompt = createResumePrompt(resumeText, vacancyInfo);
        String response = callOpenAI(prompt);
        
        Map<String, String> result = new HashMap<>();
        result.put("customizedResume", response);
        return result;
    }

    public Map<String, String> generateCoverLetter(String resumeText, Map<String, Object> vacancyInfo) {
        String prompt = createCoverLetterPrompt(resumeText, vacancyInfo);
        String response = callOpenAI(prompt);
        
        Map<String, String> result = new HashMap<>();
        result.put("coverLetter", response);
        return result;
    }

    private String createResumePrompt(String resumeText, Map<String, Object> vacancyInfo) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Ты - эксперт по составлению резюме. ");
        prompt.append("На основе предоставленного резюме и информации о вакансии создай кастомизированное резюме. ");
        prompt.append("Адаптируй опыт, навыки и достижения под требования вакансии. ");
        prompt.append("Сохрани структуру и формат, но оптимизируй содержание.\n\n");
        
        prompt.append("Исходное резюме:\n").append(resumeText).append("\n\n");
        
        prompt.append("Информация о вакансии:\n");
        if (vacancyInfo.containsKey("name")) {
            prompt.append("Название: ").append(vacancyInfo.get("name")).append("\n");
        }
        if (vacancyInfo.containsKey("description")) {
            prompt.append("Описание: ").append(vacancyInfo.get("description")).append("\n");
        }
        if (vacancyInfo.containsKey("requirements")) {
            prompt.append("Требования: ").append(vacancyInfo.get("requirements")).append("\n");
        }
        
        prompt.append("\nСоздай кастомизированное резюме:");
        return prompt.toString();
    }

    private String createCoverLetterPrompt(String resumeText, Map<String, Object> vacancyInfo) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Ты - эксперт по составлению сопроводительных писем. ");
        prompt.append("На основе резюме и информации о вакансии создай убедительное сопроводительное письмо. ");
        prompt.append("Письмо должно быть персонализированным, показывать мотивацию и соответствие требованиям.\n\n");
        
        prompt.append("Резюме:\n").append(resumeText).append("\n\n");
        
        prompt.append("Информация о вакансии:\n");
        if (vacancyInfo.containsKey("name")) {
            prompt.append("Название: ").append(vacancyInfo.get("name")).append("\n");
        }
        if (vacancyInfo.containsKey("description")) {
            prompt.append("Описание: ").append(vacancyInfo.get("description")).append("\n");
        }
        
        prompt.append("\nСоздай сопроводительное письмо:");
        return prompt.toString();
    }

    private String callOpenAI(String prompt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(openAIConfig.getApiKey());

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", openAIConfig.getModel());
        
        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> message = new HashMap<>();
        message.put("role", "user");
        message.put("content", prompt);
        messages.add(message);
        requestBody.put("messages", messages);
        requestBody.put("max_tokens", 2000);
        requestBody.put("temperature", 0.7);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                openAIConfig.getApiUrl(), 
                request, 
                Map.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                List<Map<String, Object>> choices = (List<Map<String, Object>>) response.getBody().get("choices");
                if (choices != null && !choices.isEmpty()) {
                    Map<String, Object> choice = choices.get(0);
                    Map<String, Object> messageResponse = (Map<String, Object>) choice.get("message");
                    return (String) messageResponse.get("content");
                }
            }
            
            throw new RuntimeException("Ошибка получения ответа от OpenAI");
        } catch (Exception e) {
            throw new RuntimeException("Ошибка вызова OpenAI API: " + e.getMessage(), e);
        }
    }
} 