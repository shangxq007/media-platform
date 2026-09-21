package com.example.platform.render.api;
import java.util.List;
public interface ExportOptionQuery {
 record Option(String name,boolean allowed,String recommendedPreset,String provider) {}
 List<Option> options(String tier);
}
