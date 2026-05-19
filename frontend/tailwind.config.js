/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{vue,js,ts,jsx,tsx}'],
  theme: {
    extend: {
      colors: {
        'timeline-bg': '#1a1a2e',
        'track-bg': '#16213e',
        'clip-video': '#4361ee',
        'clip-audio': '#2ec4b6',
        'clip-text': '#ff6b6b',
        'panel-bg': '#0f3460',

        'bg-base': 'var(--color-bg-base)',
        'bg-surface': 'var(--color-bg-surface)',
        'bg-surface-elevated': 'var(--color-bg-surface-elevated)',
        'bg-surface-hover': 'var(--color-bg-surface-hover)',
        'bg-overlay': 'var(--color-bg-overlay)',

        'border-default': 'var(--color-border-default)',
        'border-strong': 'var(--color-border-strong)',
        'border-focus': 'var(--color-border-focus)',

        'text-primary': 'var(--color-text-primary)',
        'text-secondary': 'var(--color-text-secondary)',
        'text-muted': 'var(--color-text-muted)',
        'text-inverse': 'var(--color-text-inverse)',
        'text-link': 'var(--color-text-link)',

        'primary-50': 'var(--color-primary-50)',
        'primary-100': 'var(--color-primary-100)',
        'primary-200': 'var(--color-primary-200)',
        'primary-300': 'var(--color-primary-300)',
        'primary-400': 'var(--color-primary-400)',
        'primary-500': 'var(--color-primary-500)',
        'primary-600': 'var(--color-primary-600)',
        'primary-700': 'var(--color-primary-700)',
        'primary-800': 'var(--color-primary-800)',
        'primary-900': 'var(--color-primary-900)',

        'success-50': 'var(--color-success-50)',
        'success-500': 'var(--color-success-500)',
        'success-600': 'var(--color-success-600)',
        'success-700': 'var(--color-success-700)',

        'warning-50': 'var(--color-warning-50)',
        'warning-500': 'var(--color-warning-500)',
        'warning-600': 'var(--color-warning-600)',
        'warning-700': 'var(--color-warning-700)',

        'danger-50': 'var(--color-danger-50)',
        'danger-500': 'var(--color-danger-500)',
        'danger-600': 'var(--color-danger-600)',
        'danger-700': 'var(--color-danger-700)',

        'info-50': 'var(--color-info-50)',
        'info-500': 'var(--color-info-500)',
        'info-600': 'var(--color-info-600)',
        'info-700': 'var(--color-info-700)',

        'risk-low': 'var(--color-risk-low)',
        'risk-medium': 'var(--color-risk-medium)',
        'risk-high': 'var(--color-risk-high)',
        'risk-critical': 'var(--color-risk-critical)',
      },
      spacing: {
        'xs': 'var(--spacing-xs)',
        'sm': 'var(--spacing-sm)',
        'md': 'var(--spacing-md)',
        'lg': 'var(--spacing-lg)',
        'xl': 'var(--spacing-xl)',
        '2xl': 'var(--spacing-2xl)',
        '3xl': 'var(--spacing-3xl)',
      },
      borderRadius: {
        'sm': 'var(--radius-sm)',
        'md': 'var(--radius-md)',
        'lg': 'var(--radius-lg)',
        'xl': 'var(--radius-xl)',
        'full': '9999px',
      },
      boxShadow: {
        'sm': 'var(--shadow-sm)',
        'md': 'var(--shadow-md)',
        'lg': 'var(--shadow-lg)',
        'xl': 'var(--shadow-xl)',
      },
      fontSize: {
        'xs': 'var(--font-size-xs)',
        'sm': 'var(--font-size-sm)',
        'base': 'var(--font-size-base)',
        'lg': 'var(--font-size-lg)',
        'xl': 'var(--font-size-xl)',
        '2xl': 'var(--font-size-2xl)',
        '3xl': 'var(--font-size-3xl)',
      },
      transitionDuration: {
        'fast': 'var(--duration-fast)',
        'normal': 'var(--duration-normal)',
        'slow': 'var(--duration-slow)',
      },
      zIndex: {
        'dropdown': 'var(--z-dropdown)',
        'sticky': 'var(--z-sticky)',
        'overlay': 'var(--z-overlay)',
        'modal': 'var(--z-modal)',
        'tooltip': 'var(--z-tooltip)',
      },
    }
  },
  plugins: []
}
