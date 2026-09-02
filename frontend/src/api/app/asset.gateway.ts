import type { AssetGateway } from '../../product/timeline/gateways'

export const assetGateway: AssetGateway = {
  async listSourcePins() {
    return { ok: false, code: 'UNAVAILABLE', message: 'No safe media browser projection supplies the full canonical source pin.', details: [], retryable: false }
  },
}
