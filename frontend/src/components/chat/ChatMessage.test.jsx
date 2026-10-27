import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render } from '@testing-library/react'
import ChatMessage from './ChatMessage.jsx'
import ChatThread from './ChatThread.jsx'

afterEach(cleanup)

const answer = {
  id: 2, role: 'ASSISTANT', status: 'COMPLETE', answerStatus: 'GROUNDED', content: 'Use OAuth [K1].',
  citations: [
    { id: 'K1', kind: 'PROJECT_KNOWLEDGE', label: 'Auth guide', excerpt: 'OAuth tokens…', available: true, source: { type: 'DOCUMENT_CHUNK', filename: 'auth.md' } },
    { id: 'K2', kind: 'PROJECT_KNOWLEDGE', label: 'Old guide', excerpt: null, available: false, source: { type: 'DOCUMENT_CHUNK', filename: 'old.md' } },
  ],
  warnings: ['Removed citation(s) that do not match retrieved evidence: K9'],
  missingInformation: ['Token lifetime'],
  followUpQuestions: ['How are tokens refreshed?'],
}

describe('ChatMessage', () => {
  it('renders the answer status, citations, warnings and missing information', () => {
    const { container, getByText } = render(<ChatMessage message={answer} />)
    expect(getByText('Grounded in evidence')).toBeTruthy()
    expect(container.querySelectorAll('.chat-sources .chat-citation')).toHaveLength(2)
    expect(container.textContent).toContain('K9')
    expect(container.textContent).toContain('Token lifetime')
  })

  it('says when a cited source is no longer available instead of showing an excerpt', () => {
    const { getByRole, container } = render(<ChatMessage message={answer} />)
    fireEvent.click(getByRole('button', { name: 'Evidence K2 (source no longer available)' }))
    expect(container.textContent).toContain('no longer available')
  })

  it('renders not-found and clarification states distinctly', () => {
    const { getByText, unmount } = render(<ChatMessage message={{ ...answer, answerStatus: 'NOT_FOUND_IN_KNOWLEDGE', citations: [] }} />)
    expect(getByText('Not found in knowledge')).toBeTruthy()
    unmount()
    const clarify = render(<ChatMessage message={{ ...answer, answerStatus: 'NEEDS_CLARIFICATION', clarifyingQuestion: 'Which service?' }} />)
    expect(clarify.getByText('Which service?')).toBeTruthy()
  })

  it('shows a pending state and an error with retry', () => {
    const pending = render(<ChatMessage message={{ id: 3, role: 'ASSISTANT', status: 'PENDING', content: '' }} />)
    expect(pending.container.querySelector('[aria-busy="true"]')).toBeTruthy()
    pending.unmount()
    const onRetry = vi.fn()
    const failed = { id: 4, role: 'ASSISTANT', status: 'ERROR', content: 'The AI model did not respond in time.' }
    const { getByRole } = render(<ChatMessage message={failed} onRetry={onRetry} />)
    fireEvent.click(getByRole('button', { name: /retry/i }))
    expect(onRetry).toHaveBeenCalledWith(failed)
  })

  it('renders user text as plain text and shows redaction flags', () => {
    const { container } = render(<ChatMessage message={{ id: 1, role: 'USER', content: '<b>hi</b> token=[REDACTED]', redactionCount: 1 }} />)
    expect(container.querySelector('b')).toBeNull()
    expect(container.textContent).toContain('<b>hi</b>')
    expect(container.textContent).toContain('1 secret(s) redacted')
  })
})

describe('ChatThread', () => {
  it('shows suggested questions in the empty state and asks the selected one', () => {
    const onAsk = vi.fn()
    const { getByRole } = render(<ChatThread messages={[]} suggestions={['Explain the architecture of Payments.']} onAsk={onAsk} />)
    fireEvent.click(getByRole('button', { name: 'Explain the architecture of Payments.' }))
    expect(onAsk).toHaveBeenCalledWith('Explain the architecture of Payments.')
  })

  it('offers follow-up questions only on the latest answer', () => {
    const onAsk = vi.fn()
    const { getAllByRole } = render(<ChatThread messages={[{ id: 1, role: 'USER', content: 'Q' }, answer]} onAsk={onAsk} />)
    fireEvent.click(getAllByRole('button', { name: 'How are tokens refreshed?' })[0])
    expect(onAsk).toHaveBeenCalledWith('How are tokens refreshed?')
  })
})
