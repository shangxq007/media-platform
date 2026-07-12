import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from '@tanstack/react-router'
import api from '../api/index'

interface AccessDescriptor {
  productId?: string
  artifactId?: string
  accessType: string
  method?: string
  url?: string
  expiresAt?: string
  ttlSeconds?: number
  mimeType?: string
  filename?: string
  sizeBytes?: number
  status?: string
  message?: string
  redacted?: boolean
}

function useAccessDescriptor(productId: string | undefined) {
  return useQuery({
    queryKey: ['access-descriptor', productId],
    queryFn: () => api.get(`/products/${productId}/access`).then(r => r.data as AccessDescriptor),
    enabled: !!productId,
  })
}

export default function UserRenderResultDetailPage() {
  const { productId } = useParams({ strict: false })
  const { data: access, isLoading, error, refetch } = useAccessDescriptor(productId)
  const [previewError, setPreviewError] = useState(false)

  const isExpired = access?.expiresAt && new Date(access.expiresAt) < new Date()

  return (
    <div className="min-h-screen bg-gray-950 text-gray-100">
      <div className="border-b border-gray-800 px-6 py-4">
        <div className="flex items-center justify-between">
          <div>
            <Link to="/app/renders" className="text-sm text-blue-400 hover:underline">&larr; Back to renders</Link>
            <h1 className="mt-1 text-xl font-bold">Render Result</h1>
          </div>
          <span className="rounded bg-green-900 px-2 py-1 text-xs text-green-200">User App</span>
        </div>
      </div>

      <div className="p-6">
        <div className="mb-6 rounded-lg border border-gray-800 bg-gray-900 p-4">
          <h2 className="mb-3 font-semibold">Result Details</h2>
          <div className="grid grid-cols-2 gap-2 text-sm">
            <div className="text-gray-400">Product ID</div>
            <div className="font-mono text-xs">{productId?.slice(0, 24)}...</div>
            {access?.mimeType && (<><div className="text-gray-400">Type</div><div>{access.mimeType}</div></>)}
            {access?.filename && (<><div className="text-gray-400">Filename</div><div>{access.filename}</div></>)}
            {access?.sizeBytes && (<><div className="text-gray-400">Size</div><div>{(access.sizeBytes / 1024 / 1024).toFixed(1)} MB</div></>)}
          </div>
        </div>

        {isLoading ? (
          <div className="text-center text-gray-400">Loading access...</div>
        ) : error ? (
          <div className="rounded-lg border border-red-800 bg-red-950 p-4 text-center text-red-200">Failed to load result access</div>
        ) : !access ? (
          <div className="rounded-lg border border-gray-800 bg-gray-900 p-6 text-center"><div className="text-lg font-semibold">Result not found</div></div>
        ) : access.accessType === 'SIGNED_URL' && access.url ? (
          <div className="rounded-lg border border-gray-800 bg-gray-900 p-4">
            <div className="mb-3 flex items-center justify-between">
              <h3 className="font-semibold">Preview</h3>
              {access.expiresAt && (
                <span className={`text-xs ${isExpired ? 'text-red-400' : 'text-gray-400'}`}>
                  {isExpired ? 'Expired' : `Expires: ${new Date(access.expiresAt).toLocaleTimeString()}`}
                </span>
              )}
            </div>
            {isExpired ? (
              <div className="text-center">
                <p className="mb-2 text-sm text-gray-400">Access expired</p>
                <button onClick={() => refetch()} className="rounded bg-blue-600 px-4 py-2 text-sm text-white hover:bg-blue-700">Refresh Access</button>
              </div>
            ) : (
              <>
                {access.mimeType?.startsWith('video/') && !previewError ? (
                  <video src={access.url} controls className="w-full rounded" onError={() => setPreviewError(true)} />
                ) : (
                  <div className="text-center text-gray-400">Preview not available for this file type</div>
                )}
                <div className="mt-3 flex gap-2">
                  <a href={access.url} target="_blank" rel="noopener noreferrer" className="rounded bg-blue-600 px-4 py-2 text-sm text-white hover:bg-blue-700">Open / Download</a>
                  <button onClick={() => refetch()} className="rounded bg-gray-700 px-4 py-2 text-sm text-gray-300 hover:bg-gray-600">Refresh Access</button>
                </div>
              </>
            )}
          </div>
        ) : access.accessType === 'UNSUPPORTED' ? (
          <div className="rounded-lg border border-gray-800 bg-gray-900 p-6 text-center">
            <div className="text-lg font-semibold">Access not available</div>
            <p className="mt-2 text-sm text-gray-400">This result is not available for browser access yet.</p>
          </div>
        ) : access.accessType === 'NOT_READY' ? (
          <div className="rounded-lg border border-gray-800 bg-gray-900 p-6 text-center">
            <div className="text-lg font-semibold">Not ready</div>
            <p className="mt-2 text-sm text-gray-400">This render is not ready yet.</p>
          </div>
        ) : access.accessType === 'STORAGE_MISSING' ? (
          <div className="rounded-lg border border-red-800 bg-red-950 p-6 text-center">
            <div className="text-lg font-semibold">Result not found</div>
            <p className="mt-2 text-sm text-red-300">The stored result could not be found. Please contact support.</p>
          </div>
        ) : (
          <div className="rounded-lg border border-gray-800 bg-gray-900 p-6 text-center">
            <div className="text-lg font-semibold">Access unavailable</div>
            <p className="mt-2 text-sm text-gray-400">{access.message || 'Unable to access this result.'}</p>
          </div>
        )}
      </div>
    </div>
  )
}
