<script setup lang="ts">
import { computed, type Component } from 'vue'
import {
  Activity,
  BarChart3,
  Bell,
  BellOff,
  Bot,
  Calendar,
  ChevronLeft,
  ChevronRight,
  Circle,
  Clapperboard,
  CreditCard,
  FileText,
  Film,
  FolderOpen,
  History,
  KeyRound,
  LayoutDashboard,
  LineChart,
  MessageCircle,
  Monitor,
  Moon,
  Package,
  PanelLeftClose,
  PanelLeftOpen,
  Pause,
  Play,
  Plus,
  Redo2,
  Save,
  Search,
  Settings,
  Share2,
  Shield,
  SkipBack,
  SkipForward,
  Sparkles,
  Sun,
  Trash2,
  Undo2,
  Upload,
  Users,
  Wallet,
  Wand2,
  X,
  Zap,
  ZoomIn,
  ZoomOut,
  type LucideIcon,
} from '@lucide/vue'

const registry: Record<string, LucideIcon> = {
  activity: Activity,
  'bar-chart-3': BarChart3,
  bell: Bell,
  'bell-off': BellOff,
  bot: Bot,
  calendar: Calendar,
  'chevron-left': ChevronLeft,
  'chevron-right': ChevronRight,
  clapperboard: Clapperboard,
  'credit-card': CreditCard,
  'file-text': FileText,
  film: Film,
  'folder-open': FolderOpen,
  history: History,
  'key-round': KeyRound,
  'layout-dashboard': LayoutDashboard,
  'line-chart': LineChart,
  'message-circle': MessageCircle,
  monitor: Monitor,
  moon: Moon,
  package: Package,
  'panel-left-close': PanelLeftClose,
  'panel-left-open': PanelLeftOpen,
  pause: Pause,
  play: Play,
  plus: Plus,
  'redo-2': Redo2,
  save: Save,
  search: Search,
  settings: Settings,
  'share-2': Share2,
  shield: Shield,
  'skip-back': SkipBack,
  'skip-forward': SkipForward,
  sparkles: Sparkles,
  sun: Sun,
  trash: Trash2,
  'undo-2': Undo2,
  upload: Upload,
  users: Users,
  wallet: Wallet,
  'wand-2': Wand2,
  x: X,
  zap: Zap,
  'zoom-in': ZoomIn,
  'zoom-out': ZoomOut,
}

const props = withDefaults(
  defineProps<{
    name: string
    size?: number | string
    strokeWidth?: number
  }>(),
  { size: 18, strokeWidth: 1.75 }
)

const Icon = computed((): Component => registry[props.name] ?? Circle)
</script>

<template>
  <component
    :is="Icon"
    :size="size"
    :stroke-width="strokeWidth"
    class="app-icon shrink-0"
    aria-hidden="true"
  />
</template>

<style scoped>
.app-icon {
  display: inline-block;
  vertical-align: middle;
}
</style>
