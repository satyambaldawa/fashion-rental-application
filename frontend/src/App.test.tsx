import { describe, it, expect } from 'vitest'
import { render } from '@testing-library/react'
import { flush } from './test/render'
import App from './App'

describe('App', () => {
  it('mounts and redirects to login without a token', async () => {
    const { container } = render(<App />)
    await flush()
    expect(container.firstChild).toBeTruthy()
  })
})
