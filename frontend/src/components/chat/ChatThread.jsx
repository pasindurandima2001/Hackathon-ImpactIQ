import { useEffect, useRef } from 'react'
import { Sparkles } from 'lucide-react'
import ChatMessage from './ChatMessage.jsx'

export function SuggestedQuestions({ questions, onAsk, disabled }) {
  if (!questions?.length) return null
  return (
    <div className="chat-suggestions">
      <span className="eyebrow"><Sparkles size={11} /> SUGGESTED QUESTIONS</span>
      <div>
        {questions.map((question) => (
          <button type="button" key={question} className="chat-suggestion" onClick={() => onAsk(question)} disabled={disabled}>{question}</button>
        ))}
      </div>
    </div>
  )
}

/** Message list with an empty state; scrolls to the newest message. */
export default function ChatThread({ messages, suggestions, onAsk, onRetry, busy, retryingId }) {
  const endRef = useRef(null)
  useEffect(() => {
    endRef.current?.scrollIntoView?.({ block: 'end' })
  }, [messages.length, messages[messages.length - 1]?.status])

  if (!messages.length) {
    return (
      <div className="chat-thread chat-thread-empty">
        <h3>Ask about this project</h3>
        <p>Answers use only authorised project knowledge, the attached defect and earlier investigation findings. Every claim links to its source, and missing knowledge is reported instead of guessed.</p>
        <SuggestedQuestions questions={suggestions} onAsk={onAsk} disabled={busy} />
      </div>
    )
  }
  const lastId = messages[messages.length - 1]?.id
  return (
    <div className="chat-thread" aria-live="polite">
      {messages.map((message) => (
        <ChatMessage
          key={message.id}
          message={message}
          onAsk={!busy && message.id === lastId ? onAsk : undefined}
          onRetry={message.id === lastId ? onRetry : undefined}
          retrying={retryingId === message.id}
        />
      ))}
      <div ref={endRef} />
    </div>
  )
}
