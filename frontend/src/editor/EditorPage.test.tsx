import { describe, it, expect } from 'vitest'
import { render } from '@testing-library/react'
import { EditorPage } from '../editor/EditorPage'

describe('EditorPage', () => {
  it('renders the editor page', () => {
    const { container } = render(<EditorPage />)
    expect(container.textContent).toContain('Video Editor')
  })

  it('renders caption editor', () => {
    const { container } = render(<EditorPage />)
    expect(container.textContent).toContain('Captions')
  })

  it('renders template selector', () => {
    const { container } = render(<EditorPage />)
    expect(container.textContent).toContain('Templates')
  })
})
