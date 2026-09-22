import { useEffect, useState } from 'react'
import { submitThumbnail, getThumbnail, type ThumbnailTask } from '../../api/thumbnail'
import { useParams } from '@tanstack/react-router'

export function ThumbnailWorkspace() {
  const {workspaceId,projectId}=useParams({strict:false}) as {workspaceId:string;projectId:string}; const baseUrl=''
  const [asset,setAsset]=useState(''); const [time,setTime]=useState('0'); const [task,setTask]=useState<ThumbnailTask|null>(null); const [error,setError]=useState('')
  useEffect(()=>{ if(!task||!['ADMITTED','RUNNING'].includes(task.status)) return; const timer=window.setInterval(async()=>{const r=await getThumbnail({baseUrl},workspaceId,projectId,task.taskId);if(r.success)setTask(r.data)},1000);return()=>window.clearInterval(timer)},[task,workspaceId,projectId,baseUrl])
  async function request(){setError('');const r=await submitThumbnail({baseUrl},workspaceId,projectId,{sourceAssetId:asset,timestampSeconds:Number(time),imageFormat:'jpeg',idempotencyKey:`ui-${asset}-${time}`});if(r.success)setTask(r.data);else setError(r.error.message)}
  return <main aria-label="Media thumbnail"><h1>Media thumbnail</h1><label>Source media asset ID <input value={asset} onChange={e=>setAsset(e.target.value)} /></label><label>Timestamp (seconds) <input type="number" min="0" step="0.01" value={time} onChange={e=>setTime(e.target.value)} /></label><button disabled={!asset||!time} onClick={request}>Request thumbnail</button>{error&&<p role="alert">{error}</p>}{task&&<section aria-live="polite"><p>Status: {task.status}</p>{task.failureCode&&<p role="alert">Failed: {task.failureCode}</p>}{task.status==='COMPLETED'&&<><img src={`${baseUrl}/api/tenants/${encodeURIComponent(workspaceId)}/projects/${encodeURIComponent(projectId)}/thumbnails/${task.taskId}/image`} alt="Committed thumbnail" /><a href={`${baseUrl}/api/tenants/${encodeURIComponent(workspaceId)}/projects/${encodeURIComponent(projectId)}/thumbnails/${task.taskId}/image`}>Open/download image</a></>}</section>}</main>
}
