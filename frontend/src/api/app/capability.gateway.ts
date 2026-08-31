import type { CapabilityGateway } from '../../product/timeline/gateways'

export const capabilityGateway: CapabilityGateway = {
  async getOperationCapability() {
    return { ok: true, value: { state: 'UNKNOWN', reason: 'No typed capability projection is currently available; backend validation remains authoritative.' } }
  },
}
