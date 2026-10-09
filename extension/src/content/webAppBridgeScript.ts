// Content script entry: starts the web app bridge (webAppBridge.ts) at document_start, before the web
// app's own scripts run, so they find the extension marked present on their first look.

import { getItem } from '../utils/chromeStorage'
import { WEB_APP_ORIGINS } from '../utils/config'
import { startBridge } from './webAppBridge'

startBridge({
  window,
  root: document.documentElement,
  allowedOrigins: WEB_APP_ORIGINS,
  loadConfiguredOrigin: () => getItem('webAppUrl'),
  sendToWorker: (message) => chrome.runtime.sendMessage(message),
})
