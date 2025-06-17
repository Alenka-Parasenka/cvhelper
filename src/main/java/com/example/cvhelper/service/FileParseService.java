package com.example.cvhelper.service;

import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.io.InputStream;
import org.apache.tika.exception.TikaException;

@Service
public class FileParseService {
    private final Tika tika = new Tika();

    public String parseFile(InputStream inputStream) throws IOException {
        try {
            return tika.parseToString(inputStream);
        } catch (TikaException e) {
            throw new IOException("Ошибка парсинга файла: " + e.getMessage(), e);
        }
    }
} 