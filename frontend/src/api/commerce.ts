import api from './index'

export interface CatalogProduct {
  productCode: string
  purchaseMode: string
  lineType: string
  displayName: string
  planKey?: string
  tierKey?: string
  bundleKey?: string
  priceMinor: number
  currencyCode: string
  creditAmountMinor?: number
  includedSeats?: number
}

export interface CheckoutSession {
  checkoutSessionId: string
  redirectUrl: string
  providerHint: string
}

export interface CommerceCart {
  cartId: string
  tenantId: string
  userId?: string
  lines: Array<{ productCode: string; quantity: number }>
  createdAt: string
  updatedAt: string
}

export const CommerceAPI = {
  async listProducts(): Promise<CatalogProduct[]> {
    const { data } = await api.get('/commerce/products')
    return data
  },

  async createCheckoutSession(payload: {
    tenantId: string
    productCode: string
    userId?: string
    successUrl?: string
    cancelUrl?: string
  }): Promise<CheckoutSession> {
    const { data } = await api.post('/commerce/checkout-sessions', payload)
    return data
  },

  async confirmCheckout(sessionId: string, userId?: string): Promise<unknown> {
    const { data } = await api.post(`/commerce/checkout-sessions/${sessionId}/confirm`, { userId })
    return data
  },

  async createCart(tenantId: string, userId?: string): Promise<CommerceCart> {
    const { data } = await api.post('/commerce/carts', { tenantId, userId })
    return data
  },

  async addCartLine(cartId: string, productCode: string, quantity = 1): Promise<CommerceCart> {
    const { data } = await api.post(`/commerce/carts/${cartId}/lines`, { productCode, quantity })
    return data
  },

  async checkoutCart(cartId: string, successUrl?: string, cancelUrl?: string): Promise<CheckoutSession> {
    const { data } = await api.post(`/commerce/carts/${cartId}/checkout-sessions`, { successUrl, cancelUrl })
    return data
  },
}
