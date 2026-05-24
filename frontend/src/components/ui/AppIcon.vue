<script setup lang="ts">
import { computed, type Component } from 'vue'
import {
  Activity,
  ArrowLeftRight,
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
  Flag,
  FolderOpen,
  Gauge,
  GitBranch,
  Globe,
  History,
  KeyRound,
  Landmark,
  LayoutDashboard,
  LineChart,
  MessageCircle,
  Monitor,
  Moon,
  Package,
  PanelLeftClose,
  PanelLeftOpen,
  Pause,
  PenTool,
  Play,
  Plus,
  Receipt,
  Redo2,
  Repeat,
  Save,
  Search,
  Settings,
  Share2,
  Shield,
  SkipBack,
  SkipForward,
  Sparkles,
  Star,
  Sun,
  Target,
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
  'activity': Activity,
  'arrow-left-right': ArrowLeftRight,
  'bar-chart-3': BarChart3,
  'bell': Bell,
  'bell-off': BellOff,
  'bot': Bot,
  'calendar': Calendar,
  'chevron-left': ChevronLeft,
  'chevron-right': ChevronRight,
  'clapperboard': Clapperboard,
  'credit-card': CreditCard,
  'file-text': FileText,
  'film': Film,
  'flag': Flag,
  'folder-open': FolderOpen,
  'gauge': Gauge,
  'git-branch': GitBranch,
  'globe': Globe,
  'history': History,
  'key-round': KeyRound,
  'landmark': Landmark,
  'layout-dashboard': LayoutDashboard,
  'line-chart': LineChart,
  'message-circle': MessageCircle,
  'monitor': Monitor,
  'moon': Moon,
  'package': Package,
  'panel-left-close': PanelLeftClose,
  'panel-left-open': PanelLeftOpen,
  'pause': Pause,
  'pen-tool': PenTool,
  'play': Play,
  'plus': Plus,
  'receipt': Receipt,
  'redo-2': Redo2,
  'repeat': Repeat,
  'save': Save,
  'search': Search,
  'settings': Settings,
  'share-2': Share2,
  'shield': Shield,
  'skip-back': SkipBack,
  'skip-forward': SkipForward,
  'sparkles': Sparkles,
  'star': Star,
  'sun': Sun,
  'target': Target,
  'trash': Trash2,
  'undo-2': Undo2,
  'upload': Upload,
  'users': Users,
  'wallet': Wallet,
  'wand-2': Wand2,
  'x': X,
  'zap': Zap,
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
