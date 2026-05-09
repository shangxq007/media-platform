package com.example.platform.datasource.api;

import com.example.platform.datasource.DslContextRegistry;
import com.example.platform.datasource.NamedDataSourceRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/datasources")
public class DataSourceController {

    private final NamedDataSourceRegistry namedDataSourceRegistry;
    private final DslContextRegistry dslContextRegistry;

    public DataSourceController(NamedDataSourceRegistry namedDataSourceRegistry,
                                DslContextRegistry dslContextRegistry) {
        this.namedDataSourceRegistry = namedDataSourceRegistry;
        this.dslContextRegistry = dslContextRegistry;
    }

    @GetMapping
    public Map<String, Object> list() {
        return Map.of(
                "dataSources", namedDataSourceRegistry.all().keySet(),
                "dslContexts", dslContextRegistry.all().keySet()
        );
    }
}
