import { useRef, useState } from 'react'
import { SendHorizontal } from 'lucide-react'

/**
 * Multiline composer. Enter sends, Shift+Enter adds a line. The limit mirrors the server-side
 * impactiq.chat.max-message-chars, which the backend enforces independently.
 */
export default function ChatComposer({ onSend, busy, disabled, maxChars = 4000, placeholder }) {
  const [text, setText] = useState('')
  const submitting = useRef(false)
  const length = text.trim().length
  const tooLong = text.length > maxChars
  const blocked = busy || disabled || length === 0 || tooLong

  async function submit(event) {
    event?.preventDefault()
    // The ref blocks a second Enter/click that arrives before the parent's busy state re-renders.
    if (blocked || submitting.current) return
    submitting.current = true
    try {
      const accepted = await onSend(text.trim())
      if (accepted !== false) setText('')
    } finally {
      submitting.current = false
    }
  }

  function onKeyDown(event) {
    if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing) {
      event.preventDefault()
      submit()
    }
  }

  return (
    <form className="chat-composer" onSubmit={submit}>
      <label className="sr-only" htmlFor="chat-input">Message</label>
      <textarea
        id="chat-input"
        value={text}
        onChange={(event) => setText(event.target.value)}
        onKeyDown={onKeyDown}
        placeholder={placeholder || 'Ask about architecture, APIs, components or the attached defect…'}
        rows={2}
        disabled={disabled}
        aria-invalid={tooLong}
        aria-describedby="chat-input-help"
      />
      <div className="chat-composer-bar">
        <span id="chat-input-help" className={tooLong ? 'chat-counter chat-counter-over' : 'chat-counter'}>
          {text.length.toLocaleString()} / {maxChars.toLocaleString()}
          {tooLong ? ' · message is too long' : ' · Enter to send, Shift+Enter for a new line'}
        </span>
        <button type="submit" className="button button-primary" disabled={blocked}>
          <SendHorizontal size={15} />{busy ? 'Sending…' : 'Send'}
        </button>
      </div>
    </form>
  )
}
