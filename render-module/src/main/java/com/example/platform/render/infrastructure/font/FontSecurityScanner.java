package com.example.platform.render.infrastructure.font;

import java.io.InputStream;
import java.nio.file.Path;

public interface FontSecurityScanner {

    String scannerName();

    boolean productionSafe();

    FontSecurityResult scan(Path fontFile);

    FontSecurityResult scan(InputStream fontData, String fileName);
}
