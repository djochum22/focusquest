// The "distracting website" the suite blocks: one page, served on 127.0.0.1. Chromium reaches it
// as http://distraction.example:15180/ through a host-resolver rule (see fixtures.ts).
import { createServer } from 'node:http'

const PORT = 15180

createServer((_request, response) => {
  response.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' })
  response.end('<!doctype html><title>Distraction</title><h1>Distraction site</h1>')
}).listen(PORT, '127.0.0.1', () => console.log(`distraction site on http://127.0.0.1:${PORT}/`))
