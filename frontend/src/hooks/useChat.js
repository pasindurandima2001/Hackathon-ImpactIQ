import { useCallback, useEffect, useRef, useState } from 'react'
import { chatApi, getApiErrorMessage, getRetryAfterSeconds } from '../services/api.js'

const PENDING_POLL_MS = 3000
let tempIds = 0

function tempMessage(role, content) {
  tempIds -= 1
  return { id: `temp${tempIds}`, role, status: role === 'USER' ? 'COMPLETE' : 'PENDING', content, citations: [], temp: true }
}

/**
 * Conversation list, the open conversation and message sending. History lives on the server, so leaving the
 * page and coming back reloads it; a reply still PENDING on the server is polled until it finishes.
 */
export default function useChat() {
  const [conversations, setConversations] = useState([])
  const [listState, setListState] = useState({ loading: true, error: '' })
  const [detail, setDetail] = useState(null)
  const [detailState, setDetailState] = useState({ loading: false, error: '' })
  const [sending, setSending] = useState(false)
  const [sendError, setSendError] = useState(null)
  const [retryingId, setRetryingId] = useState(null)
  const activeId = useRef(null)
  // Guards against a second send in the same tick, before the sending state has re-rendered.
  const inFlight = useRef(false)

  const refreshList = useCallback(async () => {
    setListState((state) => ({ ...state, loading: true }))
    try {
      setConversations(await chatApi.list())
      setListState({ loading: false, error: '' })
    } catch (error) {
      setListState({ loading: false, error: getApiErrorMessage(error, 'Could not load conversations.') })
    }
  }, [])

  useEffect(() => { refreshList() }, [refreshList])

  const upsertSummary = useCallback((summary) => {
    if (!summary) return
    setConversations((items) => [summary, ...items.filter((item) => item.id !== summary.id)])
  }, [])

  const load = useCallback(async (id, { quiet = false } = {}) => {
    activeId.current = id
    if (!quiet) setDetailState({ loading: true, error: '' })
    try {
      const loaded = await chatApi.get(id)
      if (activeId.current !== id) return
      setDetail(loaded)
      setDetailState({ loading: false, error: '' })
    } catch (error) {
      if (activeId.current !== id) return
      if (!quiet) setDetail(null)
      setDetailState({ loading: false, error: getApiErrorMessage(error, 'Could not open this conversation.'), status: error?.response?.status })
    }
  }, [])

  const open = useCallback((id) => {
    setSendError(null)
    if (!id) {
      activeId.current = null
      setDetail(null)
      setDetailState({ loading: false, error: '' })
      return
    }
    if (detail?.conversation?.id === id) return
    load(id)
  }, [detail?.conversation?.id, load])

  // A reply that is still running on the server (e.g. after navigating away mid-send) is polled until done.
  const hasPending = detail?.messages?.some((message) => message.status === 'PENDING' && !message.temp)
  useEffect(() => {
    if (!hasPending || sending) return undefined
    const timer = setInterval(() => load(detail.conversation.id, { quiet: true }), PENDING_POLL_MS)
    return () => clearInterval(timer)
  }, [hasPending, sending, detail?.conversation?.id, load])

  const create = useCallback(async ({ projectId, componentId, defectId }) => {
    const created = await chatApi.create({
      projectId: Number(projectId),
      componentId: componentId ? Number(componentId) : null,
      defectId: defectId ? Number(defectId) : null,
    })
    activeId.current = created.conversation.id
    setDetail(created)
    setDetailState({ loading: false, error: '' })
    upsertSummary(created.conversation)
    return created
  }, [upsertSummary])

  const applyReply = useCallback((conversationId, response, tempUserId, tempAssistantId) => {
    upsertSummary(response.conversation)
    if (activeId.current !== conversationId) return
    setDetail((current) => current && ({
      ...current,
      conversation: response.conversation,
      actions: response.actions,
      suggestedQuestions: [],
      messages: [
        ...current.messages.filter((message) => message.id !== tempUserId && message.id !== tempAssistantId
          && message.id !== response.userMessage?.id && message.id !== response.assistantMessage.id),
        ...(response.userMessage ? [response.userMessage] : []),
        response.assistantMessage,
      ],
    }))
  }, [upsertSummary])

  const send = useCallback(async (text, conversationId = detail?.conversation?.id) => {
    if (!conversationId || inFlight.current) return false
    inFlight.current = true
    setSending(true)
    setSendError(null)
    const userTemp = tempMessage('USER', text)
    const assistantTemp = tempMessage('ASSISTANT', '')
    setDetail((current) => current && ({ ...current, messages: [...current.messages, userTemp, assistantTemp] }))
    try {
      const response = await chatApi.send(conversationId, text)
      applyReply(conversationId, response, userTemp.id, assistantTemp.id)
      return true
    } catch (error) {
      setDetail((current) => current && ({ ...current, messages: current.messages.filter((message) => !message.temp) }))
      setSendError({ message: getApiErrorMessage(error, 'The message could not be sent.'), retryAfter: getRetryAfterSeconds(error), text })
      if (error?.response?.status === 404) refreshList()
      return false
    } finally {
      inFlight.current = false
      setSending(false)
    }
  }, [applyReply, detail?.conversation?.id, refreshList])

  const retry = useCallback(async (message) => {
    const conversationId = detail?.conversation?.id
    if (!conversationId || inFlight.current) return
    inFlight.current = true
    setSending(true)
    setRetryingId(message.id)
    setSendError(null)
    try {
      applyReply(conversationId, await chatApi.retry(conversationId, message.id))
    } catch (error) {
      setSendError({ message: getApiErrorMessage(error, 'The reply could not be retried.'), retryAfter: getRetryAfterSeconds(error) })
    } finally {
      inFlight.current = false
      setSending(false)
      setRetryingId(null)
    }
  }, [applyReply, detail?.conversation?.id])

  const update = useCallback(async (id, payload) => {
    try {
      const updated = await chatApi.update(id, payload)
      upsertSummary(updated.conversation)
      if (activeId.current === id) setDetail(updated)
      return true
    } catch (error) {
      setSendError({ message: getApiErrorMessage(error, 'The conversation could not be updated.') })
      return false
    }
  }, [upsertSummary])

  const remove = useCallback(async (id) => {
    try {
      await chatApi.remove(id)
      setConversations((items) => items.filter((item) => item.id !== id))
      if (activeId.current === id) {
        activeId.current = null
        setDetail(null)
      }
      return true
    } catch (error) {
      setListState((state) => ({ ...state, error: getApiErrorMessage(error, 'The conversation could not be deleted.') }))
      return false
    }
  }, [])

  return {
    conversations, listState, refreshList,
    detail, detailState, open, reload: () => detail && load(detail.conversation.id, { quiet: true }),
    create, send, retry, update, remove,
    sending, sendError, clearSendError: () => setSendError(null), retryingId,
  }
}
