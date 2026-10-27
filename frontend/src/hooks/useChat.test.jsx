import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { act, cleanup, renderHook, waitFor } from '@testing-library/react'
import useChat from './useChat.js'
import { chatApi } from '../services/api.js'

vi.mock('../services/api.js', async (importOriginal) => {
  const original = await importOriginal()
  return {
    ...original,
    chatApi: { list: vi.fn(), get: vi.fn(), create: vi.fn(), update: vi.fn(), remove: vi.fn(), send: vi.fn(), retry: vi.fn(), suggestions: vi.fn() },
  }
})

afterEach(cleanup)

const summary = { id: 5, title: 'Chat', projectId: 1, projectName: 'Payments', updatedAt: '2026-10-13T08:00:00Z' }
const detail = { conversation: summary, context: { projectRole: 'EDITOR' }, messages: [], actions: [], suggestedQuestions: ['Q?'], limits: { maxMessageChars: 4000 } }

describe('useChat', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(chatApi.list).mockResolvedValue([summary])
    vi.mocked(chatApi.get).mockResolvedValue(detail)
  })

  it('loads the conversation list and opens a conversation from the server', async () => {
    const { result } = renderHook(() => useChat())
    await waitFor(() => expect(result.current.conversations).toHaveLength(1))
    act(() => result.current.open(5))
    await waitFor(() => expect(result.current.detail?.conversation.id).toBe(5))
    expect(chatApi.get).toHaveBeenCalledWith(5)
  })

  it('shows an optimistic pending reply, then the stored answer', async () => {
    let resolveSend
    vi.mocked(chatApi.send).mockImplementation(() => new Promise((resolve) => { resolveSend = resolve }))
    const { result } = renderHook(() => useChat())
    act(() => result.current.open(5))
    await waitFor(() => expect(result.current.detail).not.toBeNull())

    let sent
    act(() => { sent = result.current.send('Explain auth') })
    await waitFor(() => expect(result.current.detail.messages).toHaveLength(2))
    expect(result.current.detail.messages[1].status).toBe('PENDING')
    expect(result.current.sending).toBe(true)

    await act(async () => {
      resolveSend({
        conversation: { ...summary, title: 'Explain auth' },
        userMessage: { id: 10, role: 'USER', status: 'COMPLETE', content: 'Explain auth' },
        assistantMessage: { id: 11, role: 'ASSISTANT', status: 'COMPLETE', answerStatus: 'GROUNDED', content: 'OAuth [K1]', citations: [] },
        actions: [],
      })
      await sent
    })
    expect(result.current.detail.messages.map((message) => message.id)).toEqual([10, 11])
    expect(result.current.conversations[0].title).toBe('Explain auth')
    expect(result.current.sending).toBe(false)
  })

  it('removes the optimistic messages and keeps retry details when the server rejects a send', async () => {
    vi.mocked(chatApi.send).mockRejectedValue({ response: { status: 429, headers: { 'retry-after': '12' }, data: { message: 'Message limit reached (10 per minute).' } } })
    const { result } = renderHook(() => useChat())
    act(() => result.current.open(5))
    await waitFor(() => expect(result.current.detail).not.toBeNull())
    let accepted
    await act(async () => { accepted = await result.current.send('Too fast') })
    expect(accepted).toBe(false)
    expect(result.current.detail.messages).toHaveLength(0)
    expect(result.current.sendError).toEqual({ message: 'Message limit reached (10 per minute).', retryAfter: 12, text: 'Too fast' })
  })

  it('ignores a second send while one is in flight', async () => {
    vi.mocked(chatApi.send).mockImplementation(() => new Promise(() => {}))
    const { result } = renderHook(() => useChat())
    act(() => result.current.open(5))
    await waitFor(() => expect(result.current.detail).not.toBeNull())
    act(() => { result.current.send('one') })
    await waitFor(() => expect(result.current.sending).toBe(true))
    let second
    await act(async () => { second = await result.current.send('two') })
    expect(second).toBe(false)
    expect(chatApi.send).toHaveBeenCalledTimes(1)
  })

  it('removes a deleted conversation from the list', async () => {
    vi.mocked(chatApi.remove).mockResolvedValue({})
    const { result } = renderHook(() => useChat())
    await waitFor(() => expect(result.current.conversations).toHaveLength(1))
    await act(async () => { await result.current.remove(5) })
    expect(result.current.conversations).toHaveLength(0)
  })
})
