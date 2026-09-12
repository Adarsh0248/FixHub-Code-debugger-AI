/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        accent: {
          50: '#f2f0ff',
          100: '#e6e2fe',
          200: '#cdc5fd',
          300: '#ab9dfb',
          400: '#8a72f8',
          500: '#6d5ef7',
          600: '#5a3ff0',
          700: '#4a2fd6',
          800: '#3d28ac',
          900: '#332489',
        },
      },
      fontFamily: {
        sans: [
          'Inter var',
          'Inter',
          '-apple-system',
          'BlinkMacSystemFont',
          'Segoe UI',
          'sans-serif',
        ],
        mono: ['"JetBrains Mono"', 'ui-monospace', 'SFMono-Regular', 'Menlo', 'monospace'],
      },
      transitionTimingFunction: {
        'out-strong': 'cubic-bezier(0.23, 1, 0.32, 1)',
        'in-out-strong': 'cubic-bezier(0.77, 0, 0.175, 1)',
      },
    },
  },
  plugins: [],
};
