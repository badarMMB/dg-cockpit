/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    "./src/**/*.{html,ts}",
  ],
  theme: {
    extend: {
      colors: {
        'primary': '#00236f',
        'background': '#f8f9fb',
        'surface': '#ffffff',
        'alert': '#ba1a1a',
      },
      fontFamily: {
        'sans': ['Inter', 'sans-serif'],
      },
      borderRadius: {
        'sm': '4px',
        'lg': '8px',
      },
      boxShadow: {
        'soft': '0px 4px 20px rgba(0, 0, 0, 0.05)',
      }
    },
  },
  plugins: [],
}
