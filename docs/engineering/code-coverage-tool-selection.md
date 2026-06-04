# Code Coverage Tool Selection

## Overview

This document describes the code coverage tools integrated into the media platform for monitoring code usage and identifying dead code branches.

## Backend: JaCoCo

### Tool Selection

| Criteria | JaCoCo | Alternatives |
|----------|--------|--------------|
| **Integration** | Native Gradle plugin | Cobertura, Clover |
| **Java Version Support** | Java 25 (0.8.13+) | Limited |
| **Report Formats** | XML, HTML, CSV | XML only |
| **Branch Coverage** | ✅ | ✅ |
| **Line Coverage** | ✅ | ✅ |
| **CI/CD Integration** | ✅ | ✅ |
| **IDE Support** | IntelliJ, Eclipse | Limited |

### Configuration

**Location**: `platform/build.gradle.kts`

```kotlin
// Applied to all subprojects
subprojects {
    apply(plugin = "jacoco")

    extensions.configure<JacocoPluginExtension>("jacoco") {
        toolVersion = "0.8.13"
    }

    tasks.withType<JacocoReport> {
        reports {
            xml.required.set(true)
            html.required.set(true)
            csv.required.set(false)
        }
    }
}
```

### Usage

```bash
# Generate coverage report for a specific module
./gradlew :render-module:test jacocoTestReport

# View HTML report
open platform/render-module/build/reports/jacoco/test/html/index.html

# View XML report (for CI integration)
cat platform/render-module/build/reports/jacoco/test/jacocoTestReport.xml
```

### Report Locations

- **HTML**: `platform/<module>/build/reports/jacoco/test/html/`
- **XML**: `platform/<module>/build/reports/jacoco/test/jacocoTestReport.xml`

### Coverage Metrics

| Metric | Description |
|--------|-------------|
| **Instruction Coverage** | Percentage of bytecode instructions executed |
| **Branch Coverage** | Percentage of branches (if/else, switch) executed |
| **Line Coverage** | Percentage of source lines executed |
| **Method Coverage** | Percentage of methods called |
| **Class Coverage** | Percentage of classes loaded |

## Frontend: Vitest Coverage (v8)

### Tool Selection

| Criteria | v8 | istanbul |
|----------|-----|----------|
| **Integration** | Built-in with Vitest | Plugin required |
| **Performance** | Fast (native V8) | Slower |
| **Report Formats** | text, html, lcov | text, html, lcov |
| **Branch Coverage** | ✅ | ✅ |
| **Line Coverage** | ✅ | ✅ |
| **Vue Support** | ✅ | ✅ |
| **TypeScript Support** | ✅ | ✅ |

### Configuration

**Location**: `platform/frontend/vitest.config.ts`

```typescript
test: {
  coverage: {
    provider: 'v8',
    reporter: ['text', 'text-summary', 'html', 'lcov'],
    reportsDirectory: './coverage',
    include: ['src/**/*.{ts,vue}'],
    exclude: [
      'src/**/*.spec.ts',
      'src/**/*.d.ts',
      'src/test-setup.ts',
      'src/**/index.ts',
      'src/**/*.stories.ts',
    ],
    thresholds: {
      statements: 0,
      branches: 0,
      functions: 0,
      lines: 0,
    },
  },
}
```

### Usage

```bash
# Run tests with coverage
cd frontend
npm run test:coverage

# View HTML report
open frontend/coverage/index.html

# View text summary
npm run test:coverage 2>&1 | grep -A 20 "Coverage"
```

### Report Locations

- **HTML**: `frontend/coverage/index.html`
- **LCOV**: `frontend/coverage/lcov.info`
- **Text**: Console output during test run

### Coverage Metrics

| Metric | Description |
|--------|-------------|
| **Statement Coverage** | Percentage of statements executed |
| **Branch Coverage** | Percentage of branches executed |
| **Function Coverage** | Percentage of functions called |
| **Line Coverage** | Percentage of lines executed |

## CI/CD Integration

### GitHub Actions Example

```yaml
name: Coverage
on: [push, pull_request]

jobs:
  backend-coverage:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '25'
      - run: ./gradlew :render-module:test jacocoTestReport
      - uses: actions/upload-artifact@v4
        with:
          name: backend-coverage
          path: platform/render-module/build/reports/jacoco/test/html/

  frontend-coverage:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: '20'
      - run: npm ci
      - run: npm run test:coverage
      - uses: actions/upload-artifact@v4
        with:
          name: frontend-coverage
          path: frontend/coverage/
```

## Coverage Thresholds

### Current Status

| Component | Statements | Branches | Functions | Lines |
|-----------|------------|----------|-----------|-------|
| **Backend (render-module)** | ~60% | ~55% | ~70% | ~60% |
| **Frontend** | ~75% | ~50% | ~74% | ~76% |

### Recommended Thresholds

| Component | Target | Timeline |
|-----------|--------|----------|
| **Backend** | 70% | Q3 2026 |
| **Frontend** | 80% | Q3 2026 |
| **Critical Paths** | 90% | Q4 2026 |

## Dead Code Detection

### Static Analysis

- **Backend**: JaCoCo identifies 0% coverage classes/methods
- **Frontend**: Vitest identifies unused exports

### Runtime Analysis

- **Feature Flags**: Monitor `editor.effectTaxonomy.enabled` usage
- **API Endpoints**: Track endpoint call frequency
- **Log Analysis**: Monitor error rates by module

### Recommended Tools

| Tool | Purpose | Integration |
|------|---------|-------------|
| **SonarQube** | Static analysis, dead code detection | CI/CD |
| **Codecov** | Coverage tracking, PR comments | GitHub |
| **Coveralls** | Coverage trends, badge generation | GitHub |

## Maintenance

### Weekly Tasks

1. Review coverage reports for new code
2. Identify 0% coverage classes
3. Update thresholds based on progress

### Monthly Tasks

1. Analyze coverage trends
2. Identify dead code branches
3. Update documentation

### Quarterly Tasks

1. Review tool versions
2. Update thresholds
3. Generate coverage reports for stakeholders

## References

- [JaCoCo Documentation](https://www.jacoco.org/jacoco/trunk/doc/)
- [Vitest Coverage Documentation](https://vitest.dev/guide/coverage.html)
- [SonarQube Documentation](https://docs.sonarqube.org/latest/)
