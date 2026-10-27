import { Children, useMemo, useState } from 'react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import { Check, Copy, ExternalLink } from 'lucide-react'
import CitationChip from './CitationChip.jsx'

const SAFE_PROTOCOLS = new Set(['http:', 'https:', 'mailto:'])
const CITE_PREFIX = '#cite-'
const CITATION_TOKEN = /\[([A-Za-z]\d{1,3}(?:\s*,\s*[A-Za-z]\d{1,3})*)\]/g
// Images are never rendered: an injected image URL could leak data to a third party when the browser fetches it.
const DISALLOWED_ELEMENTS = ['img']

/** Allows only absolute http(s)/mailto links and internal citation anchors; everything else becomes an empty href. */
export function safeUrl(url) {
  if (typeof url !== 'string') return ''
  if (url.startsWith(CITE_PREFIX)) return url
  try {
    return SAFE_PROTOCOLS.has(new URL(url).protocol) ? url : ''
  } catch {
    return ''
  }
}

/** Remark plugin: turns [K1] / [K1, D1] text tokens into citation links, but only for IDs the server verified. */
function remarkCitations({ knownIds }) {
  function split(node) {
    const parts = []
    let last = 0
    for (const match of node.value.matchAll(CITATION_TOKEN)) {
      const ids = match[1].split(',').map((id) => id.trim().toUpperCase()).filter((id) => knownIds.has(id))
      if (!ids.length) continue
      if (match.index > last) parts.push({ type: 'text', value: node.value.slice(last, match.index) })
      ids.forEach((id) => parts.push({ type: 'link', url: `${CITE_PREFIX}${id}`, children: [{ type: 'text', value: id }] }))
      last = match.index + match[0].length
    }
    if (!parts.length) return [node]
    if (last < node.value.length) parts.push({ type: 'text', value: node.value.slice(last) })
    return parts
  }
  function walk(node) {
    if (!Array.isArray(node.children) || node.type === 'link' || node.type === 'linkReference') return
    node.children = node.children.flatMap((child) => {
      if (child.type === 'text') return split(child)
      walk(child)
      return [child]
    })
  }
  return (tree) => walk(tree)
}

function CodeBlock({ children }) {
  const [copied, setCopied] = useState(false)
  const code = Children.toArray(children)[0]
  const text = String(code?.props?.children ?? '').replace(/\n$/, '')
  const language = /language-([\w-]+)/.exec(code?.props?.className || '')?.[1]
  async function copy() {
    try {
      await navigator.clipboard.writeText(text)
      setCopied(true)
      setTimeout(() => setCopied(false), 1500)
    } catch {
      setCopied(false)
    }
  }
  return (
    <div className="md-code">
      <div className="md-code-bar">
        <span>{language || 'code'}</span>
        <button type="button" onClick={copy} aria-label="Copy code">{copied ? <Check size={12} /> : <Copy size={12} />}{copied ? 'Copied' : 'Copy'}</button>
      </div>
      <pre><code>{text}</code></pre>
    </div>
  )
}

/**
 * Safe Markdown for assistant answers: GFM tables/lists/code, no raw HTML (skipHtml, no rehype-raw),
 * no images, restricted link protocols, and citation chips limited to the server-verified citation list.
 */
export default function MarkdownMessage({ content, citations = [] }) {
  const byId = useMemo(() => Object.fromEntries(citations.map((citation) => [citation.id, citation])), [citations])
  const remarkPlugins = useMemo(() => [remarkGfm, [remarkCitations, { knownIds: new Set(Object.keys(byId)) }]], [byId])
  const components = useMemo(() => ({
    a({ href, children }) {
      if (href?.startsWith(CITE_PREFIX)) {
        const id = href.slice(CITE_PREFIX.length)
        return <CitationChip id={id} citation={byId[id]} />
      }
      if (!href) return <span className="md-link-blocked" title="Link removed: unsupported protocol">{children}</span>
      let host = ''
      try { host = new URL(href).hostname } catch { host = '' }
      return (
        <a href={href} target="_blank" rel="noopener noreferrer nofollow" title={host ? `Opens ${host} in a new tab` : undefined}>
          {children}<ExternalLink size={10} className="md-external" aria-hidden="true" />
        </a>
      )
    },
    pre: CodeBlock,
    table({ children }) {
      return <div className="md-table-scroll"><table>{children}</table></div>
    },
  }), [byId])

  return (
    <div className="chat-markdown">
      <ReactMarkdown
        remarkPlugins={remarkPlugins}
        urlTransform={safeUrl}
        skipHtml
        disallowedElements={DISALLOWED_ELEMENTS}
        unwrapDisallowed
        components={components}
      >
        {content || ''}
      </ReactMarkdown>
    </div>
  )
}
