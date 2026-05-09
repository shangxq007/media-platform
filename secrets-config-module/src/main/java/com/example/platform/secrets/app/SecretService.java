package com.example.platform.secrets.app;

import org.springframework.stereotype.Service;

@Service
public class SecretService {
    public String resolve(String ref) {
        return "***resolved:" + ref + "***";
    }
}
