import js from '@eslint/js'
import tseslint from 'typescript-eslint'
import pluginVue from 'eslint-plugin-vue'

export default tseslint.config(
  // Global ignores
  { ignores: ['dist/**', 'node_modules/**', '*.config.ts'] },

  // Base JS rules
  js.configs.recommended,

  // TypeScript rules
  ...tseslint.configs.recommended,

  // Browser globals for all source files
  {
    files: ['src/**'],
    languageOptions: {
      globals: {
        window: 'readonly',
        document: 'readonly',
        navigator: 'readonly',
        console: 'readonly',
        setTimeout: 'readonly',
        setInterval: 'readonly',
        clearTimeout: 'readonly',
        clearInterval: 'readonly',
        fetch: 'readonly',
        AbortController: 'readonly',
        URL: 'readonly',
        URLSearchParams: 'readonly',
        FormData: 'readonly',
        Blob: 'readonly',
        File: 'readonly',
        FileReader: 'readonly',
        XMLHttpRequest: 'readonly',
        WebSocket: 'readonly',
        MediaSource: 'readonly',
        SourceBuffer: 'readonly',
        EventSource: 'readonly',
        crypto: 'readonly',
        atob: 'readonly',
        btoa: 'readonly',
        performance: 'readonly',
        Image: 'readonly',
        localStorage: 'readonly',
        sessionStorage: 'readonly',
        requestAnimationFrame: 'readonly',
        cancelAnimationFrame: 'readonly',
        getComputedStyle: 'readonly',
        matchMedia: 'readonly',
        alert: 'readonly',
        confirm: 'readonly',
        prompt: 'readonly',
        open: 'readonly',
        close: 'readonly',
        location: 'readonly',
        history: 'readonly',
        CustomEvent: 'readonly',
        Event: 'readonly',
        MessageEvent: 'readonly',
        StorageEvent: 'readonly',
        MediaQueryListEvent: 'readonly',
        Node: 'readonly',
        NodeListOf: 'readonly',
        DOMParser: 'readonly',
        XMLSerializer: 'readonly',
        BlobEvent: 'readonly',
        MediaRecorder: 'readonly',
        MediaStream: 'readonly',
        MediaStreamTrack: 'readonly',
        VideoEncoder: 'readonly',
        VideoFrame: 'readonly',
        EncodedVideoChunk: 'readonly',
        VideoColorSpace: 'readonly',
        AudioData: 'readonly',
        VideoDecoder: 'readonly',
        AudioDecoder: 'readonly',
        EncodedAudioChunk: 'readonly',
        AudioEncoder: 'readonly',
        MediaStreamAudioSourceNode: 'readonly',
        OfflineAudioContext: 'readonly',
        AudioContext: 'readonly',
        AnalyserNode: 'readonly',
        DOMException: 'readonly',
        ReadableStream: 'readonly',
        WritableStream: 'readonly',
        TransformStream: 'readonly',
        CompressionStream: 'readonly',
        DecompressionStream: 'readonly',
        TextEncoder: 'readonly',
        TextDecoder: 'readonly',
        queueMicrotask: 'readonly',
        structuredClone: 'readonly',
        caches: 'readonly',
        CacheStorage: 'readonly',
      },
    },
  },

  // Custom rules for all files
  {
    rules: {
      // Dynamic code execution prevention (P1-6)
      'no-eval': 'error',
      'no-new-func': 'error',
      'no-implied-eval': 'error',

      // Relaxed rules for Vue/TS compatibility
      '@typescript-eslint/no-explicit-any': 'warn',
      '@typescript-eslint/no-unused-vars': ['warn', { argsIgnorePattern: '^_' }],
    },
  },

  // Vue files
  ...pluginVue.configs['flat/essential'].map(config => ({
    ...config,
    files: ['*.vue', '**/*.vue'],
  })),
  {
    files: ['*.vue', '**/*.vue'],
    languageOptions: {
      parserOptions: {
        parser: tseslint.parser,
      },
    },
    rules: {
      'no-eval': 'error',
      'no-new-func': 'error',
      'no-implied-eval': 'error',
      'vue/multi-word-component-names': 'off',
    },
  }
)
