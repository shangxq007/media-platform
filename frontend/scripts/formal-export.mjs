import assert from 'node:assert/strict'
import { writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { createHash } from 'node:crypto'

export async function exportRevision ({ page, base, scope, project, headers, outputRoot }, revisionId, startMillis, endMillis, label, width = 320, height = 240, submitOrigin = base, rate = 24, rateDenominator = 1) {
  const submitted = await page.request.post(submitOrigin + '/api/render-jobs', {
    headers: { ...headers, 'Idempotency-Key': label },
    data: { revisionId, extent: { startMillis, endMillis, frameRateNumerator: rate, frameRateDenominator: rateDenominator, width, height },
      profile: 'default', outputFormat: 'MP4' },
  })
  assert.equal(submitted.status(), 201, await submitted.text())
  const { renderJobId } = await submitted.json()
  const deadline = Date.now() + 390000
  let finalArtifactId
  while (Date.now() < deadline && !finalArtifactId) {
    const result = await page.request.get(base + scope + `/render-jobs/${renderJobId}/result`, { headers })
    if (result.ok()) {
      const job = await result.json()
      assert(!['FAILED', 'CANCELLED'].includes(job.status), `${label}: ${JSON.stringify(job)}`)
      finalArtifactId = job.finalArtifactId
    }
    if (!finalArtifactId) await page.waitForTimeout(500)
  }
  assert(finalArtifactId, `${label} formal artifact`)
  const delivery = await page.request.post(base + `/api/projects/${project}/deliveries`, {
    headers: { ...headers, 'Idempotency-Key': `delivery-${label}` }, data: { sourceRenderJobId: renderJobId },
  })
  assert([200, 201].includes(delivery.status()), await delivery.text())
  const { deliveryId } = await delivery.json()
  const download = await page.request.get(base + `/api/projects/${project}/deliveries/${deliveryId}/result`, { headers })
  assert.equal(download.status(), 200)
  const bytes = await download.body()
  const path = join(outputRoot, `${label}.mp4`)
  writeFileSync(path, bytes)
  return { revisionId, renderJobId, finalArtifactId, deliveryId,
    sha256: createHash('sha256').update(bytes).digest('hex'), path }
}
