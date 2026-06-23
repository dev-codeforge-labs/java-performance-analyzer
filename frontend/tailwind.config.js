/** @type {import('tailwindcss').Config} */
module.exports = {
  darkMode: 'class',
  content: ['./src/**/*.{html,ts}'],
  theme: {
    extend: {
      colors: {
        health: { green: '#16a34a', yellow: '#d97706', red: '#dc2626' }
      }
    },
  },
  plugins: [],
};

