// Applies a saved light/night choice before first paint; see src/utils/theme.ts. A file of its own,
// not a script written into index.html: the backend's content security policy runs no inline script.
try {
  var theme = localStorage.getItem('focusquest.theme')
  if (theme === 'light' || theme === 'dark') document.documentElement.dataset.theme = theme
} catch (e) {}
