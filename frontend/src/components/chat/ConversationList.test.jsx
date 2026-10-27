import { afterEach, describe, expect, it, vi } from 'vitest'
import { act, cleanup, fireEvent, render } from '@testing-library/react'
import ConversationList from './ConversationList.jsx'

afterEach(cleanup)

const conversations = [
  { id: 1, title: 'Auth questions', projectName: 'Payments', updatedAt: '2026-10-13T08:00:00Z' },
  { id: 2, title: 'Checkout defect', projectName: 'Payments', defectId: 7, updatedAt: '2026-10-12T08:00:00Z' },
]

function renderList(props = {}) {
  const handlers = { onSelect: vi.fn(), onNew: vi.fn(), onRename: vi.fn().mockResolvedValue(true), onDelete: vi.fn() }
  const view = render(<ConversationList conversations={conversations} activeId={2} {...handlers} {...props} />)
  return { ...view, ...handlers }
}

describe('ConversationList', () => {
  it('shows an empty state', () => {
    const { container } = render(<ConversationList conversations={[]} onSelect={vi.fn()} onNew={vi.fn()} />)
    expect(container.textContent).toContain('No conversations yet')
  })

  it('opens a conversation, marks the active one and starts a new chat', () => {
    const { getByText, getByRole, onSelect, onNew, container } = renderList()
    fireEvent.click(getByText('Auth questions'))
    expect(onSelect).toHaveBeenCalledWith(1)
    expect(container.querySelector('[aria-current="true"]').textContent).toContain('IQ-0007')
    fireEvent.click(getByRole('button', { name: /new chat/i }))
    expect(onNew).toHaveBeenCalled()
  })

  it('asks for confirmation before deleting', () => {
    const { getByRole, onDelete } = renderList()
    fireEvent.click(getByRole('button', { name: 'Delete Auth questions' }))
    expect(onDelete).not.toHaveBeenCalled()
    fireEvent.click(getByRole('button', { name: 'Keep' }))
    fireEvent.click(getByRole('button', { name: 'Delete Auth questions' }))
    fireEvent.click(getByRole('button', { name: 'Delete' }))
    expect(onDelete).toHaveBeenCalledWith(1)
  })

  it('renames inline', async () => {
    const { getByRole, onRename } = renderList()
    fireEvent.click(getByRole('button', { name: 'Rename Auth questions' }))
    fireEvent.change(getByRole('textbox', { name: 'Conversation title' }), { target: { value: 'OAuth flow' } })
    await act(async () => { fireEvent.click(getByRole('button', { name: 'Save title' })) })
    expect(onRename).toHaveBeenCalledWith(1, 'OAuth flow')
  })
})
