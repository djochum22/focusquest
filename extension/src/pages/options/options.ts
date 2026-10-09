// The options page: the FocusQuest address, so one built extension works against any port or computer
// (utils/address.ts). Saving a new one signs the extension out; the web app connects it again.

import { loadConfiguredAddress, loadWebAppUrl, parseAddress, saveAddress } from '../../utils/address'
import { FRONTEND_URL } from '../../utils/config'

const form = document.getElementById('form') as HTMLFormElement
const input = document.getElementById('address') as HTMLInputElement
const reset = document.getElementById('reset') as HTMLButtonElement
const result = document.getElementById('result') as HTMLParagraphElement

function say(tone: 'ok' | 'bad', ...parts: (string | Node)[]): void {
  result.dataset.tone = tone
  result.replaceChildren(...parts)
}

async function saved(changed: boolean): Promise<void> {
  const url = await loadWebAppUrl()
  input.value = (await loadConfiguredAddress()) ?? ''
  if (!changed) {
    say('ok', 'Saved.')
    return
  }
  const link = Object.assign(document.createElement('a'), { href: `${url}/settings`, target: '_blank', rel: 'noopener', textContent: 'Open FocusQuest' })
  say('ok', 'Saved. ', link, ' to connect the extension there.')
}

async function save(origin: string | null): Promise<void> {
  try {
    await saved(await saveAddress(origin))
  } catch {
    say('bad', 'Could not save the address. Try again.')
  }
}

form.addEventListener('submit', (event) => {
  event.preventDefault()
  if (input.value.trim() === '') {
    void save(null)
    return
  }
  const origin = parseAddress(input.value)
  if (origin === null) say('bad', `That is not a web address. It looks like ${FRONTEND_URL}.`)
  else void save(origin)
})

reset.addEventListener('click', () => void save(null))

input.placeholder = FRONTEND_URL
void loadConfiguredAddress().then((address) => {
  input.value = address ?? ''
})
