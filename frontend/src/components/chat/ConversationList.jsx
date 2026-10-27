import { useState } from 'react'
import { Check, MessageSquarePlus, MessagesSquare, Pencil, Trash2, X } from 'lucide-react'
import { formatDate } from '../../services/api.js'

function ConversationItem({ conversation, active, onSelect, onRename, onDelete }) {
  const [mode, setMode] = useState('view')
  const [title, setTitle] = useState(conversation.title)

  async function saveTitle(event) {
    event.preventDefault()
    const value = title.trim()
    if (!value || value === conversation.title) { setMode('view'); return }
    if (await onRename(conversation.id, value) !== false) setMode('view')
  }

  if (mode === 'rename') {
    return (
      <li className="chat-conv-item editing">
        <form className="chat-conv-rename" onSubmit={saveTitle}>
          <input value={title} onChange={(event) => setTitle(event.target.value)} maxLength={120} aria-label="Conversation title" autoFocus />
          <button type="submit" className="icon-button" aria-label="Save title"><Check size={14} /></button>
          <button type="button" className="icon-button" aria-label="Cancel rename" onClick={() => { setTitle(conversation.title); setMode('view') }}><X size={14} /></button>
        </form>
      </li>
    )
  }

  return (
    <li className={`chat-conv-item${active ? ' active' : ''}`}>
      <button type="button" className="chat-conv-open" onClick={() => onSelect(conversation.id)} aria-current={active ? 'true' : undefined}>
        <strong>{conversation.title}</strong>
        <small>{[conversation.projectName, conversation.defectId ? `IQ-${String(conversation.defectId).padStart(4, '0')}` : null].filter(Boolean).join(' · ')}</small>
        <small>{formatDate(conversation.updatedAt)}</small>
      </button>
      {mode === 'confirm-delete' ? (
        <span className="chat-conv-confirm" role="group" aria-label="Confirm delete">
          <span>Delete?</span>
          <button type="button" className="button button-danger-outline button-small" onClick={() => onDelete(conversation.id)}>Delete</button>
          <button type="button" className="button button-secondary button-small" onClick={() => setMode('view')}>Keep</button>
        </span>
      ) : (
        <span className="chat-conv-tools">
          <button type="button" className="icon-button" aria-label={`Rename ${conversation.title}`} onClick={() => setMode('rename')}><Pencil size={13} /></button>
          <button type="button" className="icon-button" aria-label={`Delete ${conversation.title}`} onClick={() => setMode('confirm-delete')}><Trash2 size={13} /></button>
        </span>
      )}
    </li>
  )
}

export default function ConversationList({ conversations, activeId, onSelect, onNew, onRename, onDelete, loading }) {
  return (
    <aside className="chat-conversations" aria-label="Conversations">
      <div className="chat-conversations-head">
        <span className="eyebrow">CONVERSATIONS</span>
        <button type="button" className="button button-primary button-small" onClick={onNew}><MessageSquarePlus size={14} />New chat</button>
      </div>
      {loading && !conversations.length ? <p className="muted-note">Loading conversations…</p>
        : conversations.length === 0 ? (
          <div className="chat-conv-empty">
            <MessagesSquare size={20} />
            <strong>No conversations yet</strong>
            <span>Start a chat about a project. Conversations are private to you.</span>
          </div>
        ) : (
          <ul className="chat-conv-list">
            {conversations.map((conversation) => (
              <ConversationItem key={`${conversation.id}-${conversation.title}`} conversation={conversation} active={conversation.id === activeId}
                onSelect={onSelect} onRename={onRename} onDelete={onDelete} />
            ))}
          </ul>
        )}
    </aside>
  )
}
