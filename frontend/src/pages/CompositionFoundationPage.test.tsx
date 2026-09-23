import { describe, expect, it, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { WorkspaceSessionProvider } from '../foundation/workspaceSession'
import { CompositionFoundationPage } from './CompositionFoundationPage'
import { compositionApi } from '../api/composition'
vi.mock('../api/composition', async () => { const actual = await vi.importActual<typeof import('../api/composition')>('../api/composition'); return { ...actual, compositionApi: { ...actual.compositionApi } } })
const workflow={id:'wf',version:'1.0',name:'Restored workflow',steps:[],bindings:[],parameters:[],requiredCapabilities:[],executionModes:['ASYNCHRONOUS'],requiredAssets:['asset'],outputs:[],estimate:{units:0,unit:'u',quotaUnits:0},reliability:{cancellable:true,retryable:true,maxRetries:1},lifecycle:'DRAFT',tenantId:'t',workspaceId:'w',revision:4}
const capability={capabilityId:'media.thumbnail',version:'1.0',input:{name:'MediaAsset',version:'1'},output:{name:'ImageAsset',version:'1'},assetTypes:[],mediaTypes:[],executionModes:['ASYNCHRONOUS'],availability:'AVAILABLE',summary:'Thumbnail',estimate:{units:1,unit:'u',quotaUnits:1},reliability:{cancellable:true,retryable:true,maxRetries:1}}
function http(status:number){return Object.assign(new Error(`HTTP ${status}`),{response:{status}})}
function renderPage(){return render(<QueryClientProvider client={new QueryClient()}><WorkspaceSessionProvider workspaceId="w"><CompositionFoundationPage/></WorkspaceSessionProvider></QueryClientProvider>)}
describe('composition draft recovery',()=>{ beforeEach(()=>{sessionStorage.clear(); vi.restoreAllMocks(); vi.spyOn(compositionApi,'capabilities').mockResolvedValue([capability] as any); vi.spyOn(compositionApi,'getWorkflow').mockResolvedValue(workflow as any); vi.spyOn(compositionApi,'getApplication').mockRejectedValue(http(404))})
 it('restores workflow and catalog when application draft is missing',async()=>{renderPage(); await waitFor(()=>expect(screen.getByDisplayValue('Restored workflow')).toBeTruthy()); expect(screen.getByText(/application application is missing or deleted/)).toBeTruthy(); expect(screen.getByText(/media\.thumbnail/)).toBeTruthy()})
 it.each([401,403,409])('reports status %s independently',async status=>{vi.spyOn(compositionApi,'getApplication').mockRejectedValue(http(status)); renderPage(); await waitFor(()=>expect(screen.getByText(new RegExp(`application could not be restored \\(${status}\\)`))).toBeTruthy())})
})
