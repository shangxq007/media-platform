/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{vue,js,ts,jsx,tsx}'],
  darkMode: ['selector', '[data-theme="dark"]'],
  theme: {
    extend: {
      colors: {
        surface: {
          0: 'var(--surface-0)',
          1: 'var(--surface-1)',
          2: 'var(--surface-2)',
          3: 'var(--surface-3)',
          4: 'var(--surface-4)',
        },
        'text-primary': 'var(--text-primary)',
        'text-secondary': 'var(--text-secondary)',
        'text-tertiary': 'var(--text-tertiary)',
        'text-inverse': 'var(--text-inverse)',

        'border-subtle': 'var(--border-subtle)',
        'border-default': 'var(--border-default)',
        'border-strong': 'var(--border-strong)',

        accent: {
          50: 'var(--accent-50)',
          100: 'var(--accent-100)',
          200: 'var(--accent-200)',
          300: 'var(--accent-300)',
          400: 'var(--accent-400)',
          500: 'var(--accent-500)',
          600: 'var(--accent-600)',
          700: 'var(--accent-700)',
        },

        primary: {
          50: 'var(--accent-50)',
          100: 'var(--accent-100)',
          200: 'var(--accent-200)',
          300: 'var(--accent-300)',
          400: 'var(--accent-400)',
          500: 'var(--accent-500)',
          600: 'var(--accent-600)',
          700: 'var(--accent-700)',
        },

        success: 'var(--success)',
        'success-muted': 'var(--success-muted)',
        warning: 'var(--warning)',
        'warning-muted': 'var(--warning-muted)',
        danger: 'var(--danger)',
        'danger-muted': 'var(--danger-muted)',
        info: 'var(--info)',
        'info-muted': 'var(--info-muted)',

        'risk-low': 'var(--risk-low)',
        'risk-medium': 'var(--risk-medium)',
        'risk-high': 'var(--risk-high)',
        'risk-critical': 'var(--risk-critical)',

        'clip-video': 'var(--clip-video)',
        'clip-audio': 'var(--clip-audio)',
        'clip-subtitle': 'var(--clip-subtitle)',
        'clip-sticker': 'var(--clip-sticker)',
        'clip-effect': 'var(--clip-effect)',
        'clip-ai': 'var(--clip-ai)',

        'tier-free': 'var(--tier-free)',
        'tier-pro': 'var(--tier-pro)',
        'tier-team': 'var(--tier-team)',
      },
      fontFamily: {
        sans: ['Inter', 'system-ui', 'sans-serif'],
        mono: ['JetBrains Mono', 'Fira Code', 'monospace'],
      },
      borderRadius: {
        sm: 'var(--radius-sm)',
        md: 'var(--radius-md)',
        lg: 'var(--radius-lg)',
        xl: 'var(--radius-xl)',
      },
      boxShadow: {
        card: 'var(--shadow-sm)',
        'card-hover': 'var(--shadow-md)',
        modal: 'var(--shadow-xl)',
        glow: '0 0 20px rgba(99, 102, 241, 0.15)',
      },
      spacing: {
        'token-xs': 'var(--space-1)',
        'token-sm': 'var(--space-2)',
        'token-md': 'var(--space-4)',
        'token-lg': 'var(--space-6)',
        'token-xl': 'var(--space-8)',
        'token-2xl': 'var(--space-12)',
      },
      fontSize: {
        display: ['var(--text-display)', { lineHeight: '1.1', fontWeight: '700' }],
        h1: ['var(--text-h1)', { lineHeight: '1.2', fontWeight: '700' }],
        h2: ['var(--text-h2)', { lineHeight: '1.3', fontWeight: '600' }],
        h3: ['var(--text-h3)', { lineHeight: '1.4', fontWeight: '600' }],
        body: ['var(--text-body)', { lineHeight: '1.5' }],
        caption: ['var(--text-caption)', { lineHeight: '1.4', fontWeight: '500' }],
        micro: ['var(--text-micro)', { lineHeight: '1.3', fontWeight: '500' }],
      },
      transitionDuration: {
        fast: 'var(--duration-fast)',
        normal: 'var(--duration-normal)',
        slow: 'var(--duration-slow)',
      },
      transitionTimingFunction: {
        DEFAULT: 'var(--ease-default)',
      },
      zIndex: {
        dropdown: 'var(--z-dropdown)',
        sticky: 'var(--z-sticky)',
        overlay: 'var(--z-overlay)',
        modal: 'var(--z-modal)',
        tooltip: 'var(--z-tooltip)',
      },
      animation: {
        skeleton: 'skeleton 1.5s ease-in-out infinite',
        'fade-in': 'fadeIn 150ms ease-out',
        'slide-up': 'slideUp 250ms ease-out',
        'slide-in-right': 'slideInRight 250ms ease-out',
        'scale-in': 'scaleIn 150ms ease-out',
        'pulse-soft': 'pulseSoft 2s ease-in-out infinite',
      },
      keyframes: {
        skeleton: {
          '0%': { backgroundPosition: '200% 0' },
          '100%': { backgroundPosition: '-200% 0' },
        },
        fadeIn: {
          from: { opacity: '0' },
          to: { opacity: '1' },
        },
        slideUp: {
          from: { opacity: '0', transform: 'translateY(8px)' },
          to: { opacity: '1', transform: 'translateY(0)' },
        },
        slideInRight: {
          from: { opacity: '0', transform: 'translateX(16px)' },
          to: { opacity: '1', transform: 'translateX(0)' },
        },
        scaleIn: {
          from: { opacity: '0', transform: 'scale(0.95)' },
          to: { opacity: '1', transform: 'scale(1)' },
        },
        pulseSoft: {
          '0%, 100%': { opacity: '1' },
          '50%': { opacity: '0.7' },
        },
      },
    },
  },
  plugins: [],
}
