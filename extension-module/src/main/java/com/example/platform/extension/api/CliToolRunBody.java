package com.example.platform.extension.api;

import java.util.Map;

/**
 * Body for {@code POST .../cli-tools/{toolKey}/run}. Same keys could later be loaded from workflow / DB.
 */
public record CliToolRunBody(Map<String, String> params) {}
