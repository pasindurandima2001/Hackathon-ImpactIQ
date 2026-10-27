import { afterEach, describe, expect, it, vi } from 'vitest'
import { act, cleanup, fireEvent, render } from '@testing-library/react'
import ChatComposer from './ChatComposer.jsx'

afterEach(cleanup)

const input = (container) => container.querySelector('textarea')
const sendButton = (getByRole) => getByRole('button', { name: /send/i })

describe('ChatComposer', () => {
  it('shows a character counter and blocks messages over the limit', () => {
    const onSend = vi.fn()
    const { container, getByRole } = render(<ChatComposer onSend={onSend} maxChars={10} />)
    fireEvent.change(input(container), { target: { value: 'hello' } })
    expect(container.textContent).toContain('5 / 10')
    expect(sendButton(getByRole).disabled).toBe(false)
    fireEvent.change(input(container), { target: { value: 'x'.repeat(11) } })
    expect(container.textContent).toContain('message is too long')
    expect(sendButton(getByRole).disabled).toBe(true)
    expect(input(container).getAttribute('aria-invalid')).toBe('true')
  })

  it('does not send blank messages', () => {
    const onSend = vi.fn()
    const { container, getByRole } = render(<ChatComposer onSend={onSend} />)
    fireEvent.change(input(container), { target: { value: '   ' } })
    expect(sendButton(getByRole).disabled).toBe(true)
    fireEvent.keyDown(input(container), { key: 'Enter' })
    expect(onSend).not.toHaveBeenCalled()
  })

  it('sends on Enter, keeps Shift+Enter for new lines, and clears after success', async () => {
    const onSend = vi.fn().mockResolvedValue(true)
    const { container } = render(<ChatComposer onSend={onSend} />)
    fireEvent.change(input(container), { target: { value: 'Explain auth' } })
    fireEvent.keyDown(input(container), { key: 'Enter', shiftKey: true })
    expect(onSend).not.toHaveBeenCalled()
    await act(async () => { fireEvent.keyDown(input(container), { key: 'Enter' }) })
    expect(onSend).toHaveBeenCalledWith('Explain auth')
    expect(input(container).value).toBe('')
  })

  it('keeps the text when sending fails so the user can retry', async () => {
    const onSend = vi.fn().mockResolvedValue(false)
    const { container } = render(<ChatComposer onSend={onSend} />)
    fireEvent.change(input(container), { target: { value: 'Explain auth' } })
    await act(async () => { fireEvent.keyDown(input(container), { key: 'Enter' }) })
    expect(input(container).value).toBe('Explain auth')
  })

  it('prevents duplicate submissions while a send is in flight', async () => {
    let resolve
    const onSend = vi.fn(() => new Promise((done) => { resolve = done }))
    const { container } = render(<ChatComposer onSend={onSend} />)
    fireEvent.change(input(container), { target: { value: 'Once' } })
    fireEvent.keyDown(input(container), { key: 'Enter' })
    fireEvent.keyDown(input(container), { key: 'Enter' })
    fireEvent.submit(container.querySelector('form'))
    expect(onSend).toHaveBeenCalledTimes(1)
    await act(async () => resolve(true))
  })

  it('is disabled while the parent reports busy', () => {
    const { container, getByRole } = render(<ChatComposer onSend={vi.fn()} busy />)
    fireEvent.change(input(container), { target: { value: 'hello' } })
    expect(sendButton(getByRole).disabled).toBe(true)
    expect(sendButton(getByRole).textContent).toContain('Sending')
  })
})
