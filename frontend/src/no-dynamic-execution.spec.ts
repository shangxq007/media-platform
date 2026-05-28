import { describe, it, expect } from 'vitest'
import { readFileSync, readdirSync, statSync } from 'fs'
import { join, extname } from 'path'

const SRC_DIR = join(__dirname, '..')

function walkTsVueFiles(dir: string): string[] {
  const results: string[] = []
  for (const entry of readdirSync(dir)) {
    const fullPath = join(dir, entry)
    const stat = statSync(fullPath)
    if (stat.isDirectory()) {
      if (entry !== 'node_modules' && entry !== 'dist' && entry !== 'build') {
        results.push(...walkTsVueFiles(fullPath))
      }
    } else {
      const ext = extname(entry)
      if (['.ts', '.vue', '.js'].includes(ext) && !entry.endsWith('.spec.ts')) {
        results.push(fullPath)
      }
    }
  }
  return results
}

describe('No dynamic code execution in frontend source', () => {
  const files = walkTsVueFiles(SRC_DIR)

  it('no new Function() in source files', () => {
    const violations: string[] = []
    for (const file of files) {
      const content = readFileSync(file, 'utf-8')
      // Match new Function( but not in comments
      const lines = content.split('\n')
      for (let i = 0; i < lines.length; i++) {
        const line = lines[i].trim()
        if (line.startsWith('//') || line.startsWith('*') || line.startsWith('/*')) continue
        if (/new\s+Function\s*\(/.test(line)) {
          violations.push(`${file}:${i + 1}: ${line}`)
        }
      }
    }
    expect(violations).toEqual([])
  })

  it('no eval() in source files', () => {
    const violations: string[] = []
    for (const file of files) {
      const content = readFileSync(file, 'utf-8')
      const lines = content.split('\n')
      for (let i = 0; i < lines.length; i++) {
        const line = lines[i].trim()
        if (line.startsWith('//') || line.startsWith('*') || line.startsWith('/*')) continue
        // Match eval( but not evaluation, evaluate, etc.
        if (/\beval\s*\(/.test(line)) {
          violations.push(`${file}:${i + 1}: ${line}`)
        }
      }
    }
    expect(violations).toEqual([])
  })

  it('no string-form setTimeout in source files', () => {
    const violations: string[] = []
    for (const file of files) {
      const content = readFileSync(file, 'utf-8')
      const lines = content.split('\n')
      for (let i = 0; i < lines.length; i++) {
        const line = lines[i].trim()
        if (line.startsWith('//') || line.startsWith('*') || line.startsWith('/*')) continue
        if (/setTimeout\s*\(\s*["']/.test(line)) {
          violations.push(`${file}:${i + 1}: ${line}`)
        }
      }
    }
    expect(violations).toEqual([])
  })

  it('no string-form setInterval in source files', () => {
    const violations: string[] = []
    for (const file of files) {
      const content = readFileSync(file, 'utf-8')
      const lines = content.split('\n')
      for (let i = 0; i < lines.length; i++) {
        const line = lines[i].trim()
        if (line.startsWith('//') || line.startsWith('*') || line.startsWith('/*')) continue
        if (/setInterval\s*\(\s*["']/.test(line)) {
          violations.push(`${file}:${i + 1}: ${line}`)
        }
      }
    }
    expect(violations).toEqual([])
  })

  it('dynamic import() only used for lazy loading (not with new Function)', () => {
    const violations: string[] = []
    for (const file of files) {
      const content = readFileSync(file, 'utf-8')
      const lines = content.split('\n')
      for (let i = 0; i < lines.length; i++) {
        const line = lines[i].trim()
        if (line.startsWith('//') || line.startsWith('*') || line.startsWith('/*')) continue
        // new Function('m', 'return import(m)') pattern
        if (/new\s+Function.*import/.test(line)) {
          violations.push(`${file}:${i + 1}: ${line}`)
        }
      }
    }
    expect(violations).toEqual([])
  })
})
