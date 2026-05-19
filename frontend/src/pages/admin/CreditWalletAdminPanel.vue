<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { BillingAdminAPI } from '@/api/admin/billing-admin'
import type { CreditWallet } from '@/types'

const loading = ref(true)
const wallets = ref<CreditWallet[]>([])
const selectedWallet = ref<CreditWallet | null>(null)
const topUpAmount = ref(0)
const topUpDescription = ref('')
const processing = ref(false)

onMounted(loadWallets)

async function loadWallets() {
  loading.value = true
  try {
    wallets.value = await BillingAdminAPI.getCreditWallets()
  } catch { /* backend may not be running */ }
  loading.value = false
}

async function topUp(walletId: string) {
  if (topUpAmount.value <= 0) return
  processing.value = true
  try {
    await BillingAdminAPI.adminTopUpCredits(walletId, topUpAmount.value, topUpDescription.value || 'Admin top-up')
    topUpAmount.value = 0
    topUpDescription.value = ''
    await loadWallets()
  } catch { /* handle error */ }
  processing.value = false
}
</script>

<template>
  <div class="bg-gray-800 border border-gray-700 rounded-lg p-4 space-y-4">
    <div class="flex items-center justify-between">
      <h3 class="text-sm font-semibold text-gray-300">Credit Wallets</h3>
      <button class="text-[10px] text-blue-400 hover:text-blue-300" @click="loadWallets">Refresh</button>
    </div>

    <div v-if="loading" class="text-gray-500 text-xs">Loading...</div>
    <div v-else-if="wallets.length === 0" class="text-gray-500 text-xs">No wallets found</div>
    <div v-else class="space-y-2">
      <div v-for="wallet in wallets" :key="wallet.walletId"
        class="p-2 rounded cursor-pointer transition-colors"
        :class="selectedWallet?.walletId === wallet.walletId ? 'bg-blue-600/10 border border-blue-700/50' : 'bg-gray-700/20 hover:bg-gray-700/30'"
        @click="selectedWallet = wallet">
        <div class="flex items-center justify-between">
          <div>
            <span class="text-xs text-white font-mono">{{ wallet.subjectId }}</span>
            <span class="text-[10px] text-gray-500 ml-2">{{ wallet.subjectType }}</span>
          </div>
          <span class="text-sm font-bold text-green-400">{{ wallet.balance.toFixed(2) }} {{ wallet.currency }}</span>
        </div>
        <div v-if="wallet.heldBalance > 0" class="text-[10px] text-yellow-400 mt-1">
          Held: {{ wallet.heldBalance.toFixed(2) }}
        </div>
      </div>
    </div>

    <div v-if="selectedWallet" class="pt-3 border-t border-gray-700 space-y-2">
      <div class="text-[10px] text-gray-500">Top Up: {{ selectedWallet.subjectId }}</div>
      <div class="flex gap-2">
        <input v-model.number="topUpAmount" type="number" step="0.01" placeholder="Amount" class="flex-1 bg-gray-700 border border-gray-600 rounded px-2 py-1 text-xs text-white" />
        <input v-model="topUpDescription" placeholder="Description" class="flex-1 bg-gray-700 border border-gray-600 rounded px-2 py-1 text-xs text-white" />
      </div>
      <button class="w-full px-2 py-1 bg-green-600 hover:bg-green-500 text-white text-xs rounded" :disabled="processing || topUpAmount <= 0" @click="topUp(selectedWallet.walletId)">
        {{ processing ? 'Processing...' : 'Top Up' }}
      </button>
    </div>
  </div>
</template>
