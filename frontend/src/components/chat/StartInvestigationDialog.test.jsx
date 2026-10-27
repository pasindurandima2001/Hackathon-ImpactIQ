import { afterEach, describe, expect, it, vi } from 'vitest'
import { act, cleanup, fireEvent, render } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import StartInvestigationDialog from './StartInvestigationDialog.jsx'

afterEach(cleanup)

const action = { type: 'START_INVESTIGATION', enabled: true, label: 'Start investigation', reason: '', defectId: 7 }

function renderDialog(props) {
  return render(<MemoryRouter><StartInvestigationDialog action={action} defectLabel="IQ-0007" onClose={vi.fn()} {...props} /></MemoryRouter>)
}

describe('StartInvestigationDialog', () => {
  it('explains quota use and does nothing until the user confirms', () => {
    const start = vi.fn()
    const { container, getByRole } = renderDialog({ start })
    expect(container.textContent).toContain('uses AI quota')
    expect(start).not.toHaveBeenCalled()
    expect(document.activeElement).toBe(getByRole('button', { name: 'Cancel' }))
  })

  it('cancel closes without starting', () => {
    const start = vi.fn()
    const onClose = vi.fn()
    const { getByRole } = renderDialog({ start, onClose })
    fireEvent.click(getByRole('button', { name: 'Cancel' }))
    expect(onClose).toHaveBeenCalled()
    expect(start).not.toHaveBeenCalled()
  })

  it('calls the existing investigation API only after explicit confirmation', async () => {
    const start = vi.fn().mockResolvedValue({ summary: { id: 3 } })
    const onStarted = vi.fn()
    const { getByRole, container } = renderDialog({ start, onStarted })
    await act(async () => { fireEvent.click(getByRole('button', { name: 'Confirm and start' })) })
    expect(start).toHaveBeenCalledWith(7)
    expect(onStarted).toHaveBeenCalled()
    expect(container.textContent).toContain('The investigation was queued')
  })

  it('shows the server refusal (for example a missing EDITOR role) instead of failing silently', async () => {
    const start = vi.fn().mockRejectedValue({ response: { data: { message: 'This action requires the EDITOR project role' } } })
    const { getByRole } = renderDialog({ start })
    await act(async () => { fireEvent.click(getByRole('button', { name: 'Confirm and start' })) })
    expect(getByRole('alert').textContent).toContain('EDITOR')
  })

  it('cannot confirm a disabled action', () => {
    const { getByRole } = renderDialog({ action: { ...action, enabled: false }, start: vi.fn() })
    expect(getByRole('button', { name: 'Confirm and start' }).disabled).toBe(true)
  })
})
