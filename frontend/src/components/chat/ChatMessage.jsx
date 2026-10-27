import { AlertTriangle, Bot, CircleHelp, LoaderCircle, RotateCw, ShieldAlert, UserRound } from 'lucide-react'
import MarkdownMessage from './MarkdownMessage.jsx'
import CitationChip from './CitationChip.jsx'
import { formatDate } from '../../services/api.js'

export const ANSWER_STATUS = {
  GROUNDED: { label: 'Grounded in evidence', tone: 'grounded' },
  PARTIAL: { label: 'Partially supported', tone: 'partial' },
  NOT_FOUND_IN_KNOWLEDGE: { label: 'Not found in knowledge', tone: 'missing' },
  NEEDS_CLARIFICATION: { label: 'Needs clarification', tone: 'clarify' },
}

function UserBubble({ message }) {
  return (
    <article className="chat-row chat-row-user" aria-label="Your message">
      <div className="chat-bubble chat-bubble-user">
        <p>{message.content}</p>
        {(message.redactionCount > 0 || message.suspicious) && (
          <span className="chat-user-flags">
            {message.redactionCount > 0 && <span><ShieldAlert size={11} />{message.redactionCount} secret(s) redacted</span>}
            {message.suspicious && <span><AlertTriangle size={11} />Instruction-like text treated as data</span>}
          </span>
        )}
      </div>
      <span className="chat-avatar chat-avatar-user" aria-hidden="true"><UserRound size={15} /></span>
    </article>
  )
}

export default function ChatMessage({ message, onRetry, onAsk, retrying }) {
  if (message.role === 'USER') return <UserBubble message={message} />

  if (message.status === 'PENDING') {
    return (
      <article className="chat-row" aria-label="Assistant is answering" aria-busy="true">
        <span className="chat-avatar" aria-hidden="true"><Bot size={15} /></span>
        <div className="chat-bubble chat-bubble-pending" role="status">
          <LoaderCircle className="spin" size={15} />Searching project knowledge and preparing an answer…
        </div>
      </article>
    )
  }

  if (message.status === 'ERROR') {
    return (
      <article className="chat-row" aria-label="Assistant error">
        <span className="chat-avatar chat-avatar-error" aria-hidden="true"><Bot size={15} /></span>
        <div className="chat-bubble chat-bubble-error" role="alert">
          <p><AlertTriangle size={14} />{message.content}</p>
          {onRetry && (
            <button type="button" className="button button-secondary button-small" onClick={() => onRetry(message)} disabled={retrying}>
              <RotateCw size={13} className={retrying ? 'spin' : undefined} />{retrying ? 'Retrying…' : 'Retry'}
            </button>
          )}
        </div>
      </article>
    )
  }

  const status = ANSWER_STATUS[message.answerStatus] || ANSWER_STATUS.PARTIAL
  const citations = message.citations || []
  return (
    <article className="chat-row" aria-label="Assistant answer">
      <span className="chat-avatar" aria-hidden="true"><Bot size={15} /></span>
      <div className="chat-bubble chat-bubble-assistant">
        <div className="chat-answer-head">
          <span className={`chat-status chat-status-${status.tone}`}>{status.label}</span>
          {message.completedAt && <time dateTime={message.completedAt}>{formatDate(message.completedAt)}</time>}
        </div>
        <MarkdownMessage content={message.content} citations={citations} />
        {message.clarifyingQuestion && (
          <p className="chat-clarify"><CircleHelp size={14} />{message.clarifyingQuestion}</p>
        )}
        {message.missingInformation?.length > 0 && (
          <div className="chat-missing">
            <strong>Information still needed</strong>
            <ul>{message.missingInformation.map((item) => <li key={item}>{item}</li>)}</ul>
          </div>
        )}
        {citations.length > 0 && (
          <div className="chat-sources" aria-label="Sources">
            <span>Sources</span>
            {citations.map((citation) => <CitationChip key={citation.id} id={citation.id} citation={citation} />)}
          </div>
        )}
        {message.warnings?.length > 0 && (
          <ul className="chat-warnings">
            {message.warnings.map((warning) => <li key={warning}><AlertTriangle size={11} />{warning}</li>)}
          </ul>
        )}
        {onAsk && message.followUpQuestions?.length > 0 && (
          <div className="chat-followups">
            {message.followUpQuestions.map((question) => (
              <button type="button" key={question} className="chat-suggestion" onClick={() => onAsk(question)}>{question}</button>
            ))}
          </div>
        )}
      </div>
    </article>
  )
}
