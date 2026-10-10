package com.example.backend.global.socket;
import java.util.List;
public record RealtimeEvent(List<String> recipients, String type, Object data) {}
