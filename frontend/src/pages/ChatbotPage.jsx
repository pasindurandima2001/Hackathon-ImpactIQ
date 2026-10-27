import { useCallback, useEffect, useState } from 'react'
import { AlertCircle, BookOpenText, PanelLeft, RotateCw, X } from 'lucide-react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import ErrorState from '../components/ErrorState.jsx'
import LoadingState from '../components/LoadingState.jsx'
import ChatComposer from '../components/chat/ChatComposer.jsx'
import ChatContextBar, { ContextFields, defectKey } from '../components/chat/ChatContextBar.jsx'
import ChatThread, { SuggestedQuestions } from '../components/chat/ChatThread.jsx'
import ConversationList from '../components/chat/ConversationList.jsx'
import StartInvestigationDialog from '../components/chat/StartInvestigationDialog.jsx'
import useChat from '../hooks/useChat.js'
import { useAuth } from '../context/AuthContext.jsx'
import { chatApi, defectApi, getApiErrorMessage, projectApi } from '../services/api.js'

const DEFAULT_MAX_CHARS = 4000

function SendErrorBanner({ error, onRetry, onDismiss, busy }) {
  if (!error) return null
  return (
    <div className="chat-send-error" role="alert">
      <AlertCircle size={15} />
      <span>{error.message}{error.retryAfter ? ` (retry in about ${error.retryAfter}s)` : ''}</span>
      {error.text && <button type="button" className="button button-secondary button-small" onClick={() => onRetry(error.text)} disabled={busy}><RotateCw size={13} />Retry</button>}
      <button type="button" className="icon-button" onClick={onDismiss} aria-label="Dismiss error"><X size={14} /></button>
    </div>
  )
}

/** New-conversation setup: pick the context, see suggestions built from it, then send the first message. */
function NewConversation({ projects, defects, onStart, busy, error, onDismissError }) {
  const [context, setContext] = useState({ projectId: projects.length === 1 ? String(projects[0].id) : '', componentId: '', defectId: '' })
  const [suggestions, setSuggestions] = useState({ questions: [], limits: null, error: '' })

  useEffect(() => {
    if (!context.projectId) { setSuggestions({ questions: [], limits: null, error: '' }); return undefined }
    let cancelled = false
    chatApi.suggestions(context)
      .then((result) => { if (!cancelled) setSuggestions({ ...result, error: '' }) })
      .catch((error) => { if (!cancelled) setSuggestions({ questions: [], limits: null, error: getApiErrorMessage(error) }) })
    return () => { cancelled = true }
  }, [context])

  const start = (text) => onStart(context, text)
  return (
    <div className="chat-main chat-new">
      <section className="chat-context chat-context-setup" aria-label="Choose context">
        <div>
          <span className="eyebrow">NEW CONVERSATION</span>
          <h3>What do you want to discuss?</h3>
        </div>
        <ContextFields projects={projects} defects={defects} value={context} onChange={setContext} />
        {suggestions.error && <p className="chat-dialog-error">{suggestions.error}</p>}
      </section>
      <div className="chat-thread chat-thread-empty">
        {context.projectId ? (
          <>
            <h3>Ask about {projects.find((item) => item.id === Number(context.projectId))?.name}</h3>
            <p>Answers cite authorised project knowledge{context.defectId ? `, ${defectKey(context.defectId)}` : ''} and earlier investigation findings. If the knowledge base does not cover a question, the assistant says so.</p>
            <SuggestedQuestions questions={suggestions.questions} onAsk={start} disabled={busy} />
          </>
        ) : <p>Select a project to start. The assistant only searches projects you are a member of.</p>}
      </div>
      <SendErrorBanner error={error} busy={busy} onRetry={() => {}} onDismiss={onDismissError} />
      <ChatComposer onSend={start} busy={busy} disabled={!context.projectId} maxChars={suggestions.limits?.maxMessageChars || DEFAULT_MAX_CHARS} />
    </div>
  )
}

export default function ChatbotPage() {
  const { user } = useAuth()
  const isAdmin = user?.role === 'ADMIN'
  const { conversationId } = useParams()
  const navigate = useNavigate()
  const chat = useChat()
  const { open } = chat
  const [catalog, setCatalog] = useState({ projects: [], defects: [], loading: true, error: '' })
  const [listOpen, setListOpen] = useState(false)
  const [starting, setStarting] = useState(false)
  const [investigationAction, setInvestigationAction] = useState(null)
  const [startError, setStartError] = useState(null)

  const loadCatalog = useCallback(async () => {
    setCatalog((state) => ({ ...state, loading: true, error: '' }))
    try {
      const [projects, defects] = await Promise.all([projectApi.list(), defectApi.getDefects()])
      setCatalog({ projects, defects, loading: false, error: '' })
    } catch (error) {
      setCatalog({ projects: [], defects: [], loading: false, error: getApiErrorMessage(error, 'Could not load your projects.') })
    }
  }, [])

  useEffect(() => { loadCatalog() }, [loadCatalog])
  const numericId = conversationId ? Number(conversationId) : null
  useEffect(() => { open(numericId) }, [numericId, open])

  async function startConversation(context, text) {
    setStarting(true)
    setStartError(null)
    try {
      const created = await chat.create(context)
      navigate(`/chat/${created.conversation.id}`)
      return await chat.send(text, created.conversation.id)
    } catch (error) {
      setStartError({ message: getApiErrorMessage(error, 'The conversation could not be created.') })
      return false
    } finally {
      setStarting(false)
    }
  }

  function selectConversation(id) {
    setListOpen(false)
    navigate(`/chat/${id}`)
  }

  async function deleteConversation(id) {
    if (await chat.remove(id) && id === numericId) navigate('/chat')
  }

  const detail = chat.detail
  const busy = chat.sending || starting
  const showNew = !numericId

  let main
  if (catalog.loading && showNew) {
    main = <div className="chat-main"><LoadingState label="Loading your projects" /></div>
  } else if (catalog.error && showNew) {
    main = <div className="chat-main"><ErrorState message={catalog.error} onRetry={loadCatalog} /></div>
  } else if (showNew && catalog.projects.length === 0) {
    main = (
      <div className="chat-main chat-thread-empty">
        <BookOpenText size={22} />
        <h3>No projects yet</h3>
        {isAdmin ? (
          <>
            <p>The assistant answers from Project Knowledge. Create a project and upload documentation first.</p>
            <Link className="button button-primary" to="/knowledge">Open Project knowledge</Link>
          </>
        ) : (
          <p>You are not assigned to a project yet. Ask an administrator to add you to a project to use the assistant.</p>
        )}
      </div>
    )
  } else if (showNew) {
    main = <NewConversation projects={catalog.projects} defects={catalog.defects} onStart={startConversation} busy={busy}
      error={startError} onDismissError={() => setStartError(null)} />
  } else if (chat.detailState.loading && !detail) {
    main = <div className="chat-main"><LoadingState label="Opening conversation" /></div>
  } else if (!detail) {
    main = (
      <div className="chat-main">
        <ErrorState message={chat.detailState.status === 404 ? 'This conversation does not exist or you no longer have access to its project.' : chat.detailState.error}
          onRetry={chat.detailState.status === 404 ? undefined : () => open(numericId)} />
        <Link className="text-link" to="/chat">Start a new conversation</Link>
      </div>
    )
  } else {
    main = (
      <div className="chat-main">
        <ChatContextBar
          detail={detail}
          projects={catalog.projects}
          defects={catalog.defects}
          onChangeContext={(payload) => chat.update(detail.conversation.id, { updateContext: true, ...payload })}
          onStartInvestigation={setInvestigationAction}
        />
        <ChatThread
          messages={detail.messages}
          suggestions={detail.suggestedQuestions}
          onAsk={(text) => chat.send(text)}
          onRetry={chat.retry}
          busy={busy}
          retryingId={chat.retryingId}
        />
        <SendErrorBanner error={chat.sendError} busy={busy} onRetry={(text) => chat.send(text)} onDismiss={chat.clearSendError} />
        <ChatComposer onSend={(text) => chat.send(text)} busy={busy} maxChars={detail.limits?.maxMessageChars || DEFAULT_MAX_CHARS} />
      </div>
    )
  }

  return (
    <div className="page-stack chat-page">
      <section className="welcome-row chat-heading">
        <div>
          <span className="eyebrow">ASSIST / AI CHATBOT</span>
          <h2>Project assistant</h2>
          <p>Evidence-cited answers from your project knowledge, defects and investigation findings.</p>
        </div>
        <button type="button" className="button button-secondary chat-list-toggle" onClick={() => setListOpen((value) => !value)} aria-expanded={listOpen}>
          <PanelLeft size={15} />Conversations
        </button>
      </section>
      {chat.listState.error && <ErrorState message={chat.listState.error} onRetry={chat.refreshList} />}
      <div className={`chat-layout${listOpen ? ' list-open' : ''}`}>
        <ConversationList
          conversations={chat.conversations}
          activeId={numericId}
          loading={chat.listState.loading}
          onSelect={selectConversation}
          onNew={() => { setListOpen(false); navigate('/chat') }}
          onRename={(id, title) => chat.update(id, { title })}
          onDelete={deleteConversation}
        />
        {main}
      </div>
      {investigationAction && detail && (
        <StartInvestigationDialog
          action={investigationAction}
          defectLabel={defectKey(investigationAction.defectId)}
          onClose={() => setInvestigationAction(null)}
          onStarted={() => chat.reload()}
        />
      )}
    </div>
  )
}
