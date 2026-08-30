import { describe, expect, it } from 'vitest'
import { retainCompatibleSelection, type CrossSurfaceSelection } from './references'

describe('cross-surface reference and selection model', () => {
  it('retains compatible canonical identities and drops incompatible presentation selection', () => {
    const selection: CrossSurfaceSelection = {
      primary: { kind: 'ARTIFACT', id: 'artifact-1' },
      related: [{ kind: 'WORKFLOW', id: 'workflow-1' }, { kind: 'PROJECT', id: 'project-1' }],
      revision: { kind: 'REVISION', id: 'revision-1' },
    }
    expect(retainCompatibleSelection(selection, ['ARTIFACT', 'PROJECT'])).toEqual({
      primary: { kind: 'ARTIFACT', id: 'artifact-1' },
      related: [{ kind: 'PROJECT', id: 'project-1' }],
      revision: null,
    })
  })
})
