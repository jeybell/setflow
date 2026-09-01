<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { isAxiosError } from 'axios'
import { useRouter } from 'vue-router'
import { Plus, Trash2, ChevronRight, X, Music, List, CalendarDays, ChevronLeft, Star, Search, FileText} from '@lucide/vue'
import { createSetlist, createSetlistFromText, deleteSetlist } from '../apis/setlistApi'
import { addSetlistItem } from '../apis/setlistItemApi'
import DefaultLayout from '../layouts/DefaultLayout.vue'
import Button from '../components/ui/Button.vue'
import Input from '../components/ui/Input.vue'
import Label from '../components/ui/Label.vue'
import Textarea from '../components/ui/Textarea.vue'
import DatePicker from '../components/ui/DatePicker.vue'
import Card from '../components/ui/Card.vue'
import SongPickerModal from '../components/SongPickerModal.vue'
import { useSetlistStore } from '../stores/setlistStore'
import { useSongStore } from '../stores/songStore'
import { useToast } from '../composables/useToast'
import { useSetlistFavorites } from '../composables/useSetlistFavorites'
import type { Setlist } from '../types/setlist'

interface ApiErrorResponse { message?: string }

const router = useRouter()
const store = useSetlistStore()
const songStore = useSongStore()
const toast = useToast()
const { favoriteIds, isFavorite, toggleFavorite } = useSetlistFavorites()

// 콘티 제목·날짜뿐 아니라 포함된 곡 제목/아티스트로도 검색 (#2)
const searchQuery = ref('')
const matchesQuery = (s: Setlist) => {
  const q = searchQuery.value.trim().toLowerCase()
  if (!q) return true
  if (s.title?.toLowerCase().includes(q)) return true
  if (s.serviceDate.includes(q)) return true
  return s.items.some(
    (it) =>
      it.songTitle.toLowerCase().includes(q) ||
      (it.songArtist?.toLowerCase().includes(q) ?? false),
  )
}

const favoriteSetlists = computed(() =>
  store.setlists.filter((s) => favoriteIds.value.includes(s.setlistId) && matchesQuery(s)),
)
const otherSetlists = computed(() =>
  store.setlists.filter((s) => !favoriteIds.value.includes(s.setlistId) && matchesQuery(s)),
)
const hasSearchResults = computed(() => favoriteSetlists.value.length + otherSetlists.value.length > 0)

const apiError = (e: unknown, fallback: string) =>
  isAxiosError<ApiErrorResponse>(e) ? (e.response?.data?.message ?? fallback) : fallback

const today = () => new Date().toISOString().slice(0, 10)

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토']
// 선택한 날짜를 'YYYY.MM.DD (요일)' 형태로 표기
const formattedServiceDate = computed(() => {
  const v = createForm.serviceDate
  if (!v || !/^\d{4}-\d{2}-\d{2}$/.test(v)) return ''
  const [y, m, d] = v.split('-').map(Number)
  const dow = WEEKDAYS[new Date(y, m - 1, d).getDay()]
  return `${y}.${String(m).padStart(2, '0')}.${String(d).padStart(2, '0')} (${dow})`
})

// 기본은 목록만. '새 콘티' 버튼을 눌러야 등록 폼이 열린다.
const showCreateForm = ref(false)
const createForm = reactive({ serviceDate: today(), title: '', memo: '' })
const createError = ref('')
const isCreating = ref(false)

// 곡 추가 (폼 내)
interface PendingSong { songId: number; songSheetId: number | null; title: string }
const pendingSongs = ref<PendingSong[]>([])
const showSongPicker = ref(false)

const onSongPicked = (songId: number, songSheetId: number | null) => {
  const song = songStore.songs.find(s => s.songId === songId)
  if (!song) return
  pendingSongs.value.push({ songId, songSheetId, title: song.title })
  showSongPicker.value = false
}

// 곡 추가 모달을 열 때 비로소 곡 목록을 로드(진입 시 미리 받지 않음)
const openSongPicker = () => {
  void songStore.ensureSongsLoaded()
  showSongPicker.value = true
}

const removePendingSong = (idx: number) => { pendingSongs.value.splice(idx, 1) }

const resetCreateForm = () => {
  createForm.serviceDate = today()
  createForm.title = ''
  createForm.memo = ''
  createError.value = ''
  pendingSongs.value = []
}

const handleCreate = async () => {
  if (!createForm.serviceDate) {
    createError.value = '날짜는 필수입니다.'
    return
  }
  isCreating.value = true
  createError.value = ''
  try {
    const created = await createSetlist({
      serviceDate: createForm.serviceDate,
      title: createForm.title.trim() || null,
      memo: createForm.memo.trim() || null,
    })
    for (let i = 0; i < pendingSongs.value.length; i++) {
      const s = pendingSongs.value[i]
      await addSetlistItem(created.setlistId, { songId: s.songId, songSheetId: s.songSheetId ?? undefined, orderNo: i + 1, memo: null })
    }
    resetCreateForm()
    showCreateForm.value = false
    toast.success('콘티를 만들었어요')
    await router.push(`/setlists/${created.setlistId}`)
  } catch (e) {
    createError.value = apiError(e, '생성에 실패했습니다.')
  } finally {
    isCreating.value = false
  }
}

// ── 텍스트로 콘티 만들기 (#230 후속): 곡 순서·키·메모를 담은 텍스트를 붙여넣으면
// 백엔드가 파싱해 곡을 매칭하고 콘티를 한 번에 생성한다.
const textImportHelp = `아래처럼 첫 줄에 <날짜.제목>을 쓰고, 그 아래 곡을 순서대로 한 줄씩 붙여넣으면 등록된 곡을 찾아 콘티를 만듭니다. 줄 끝의 코드(F, G, Am 등)는 연주 키로, 괄호 표시(예: (Intro))는 메모로 들어갑니다.

<2026.9.2.(수)저녁 만나예배>
・(Intro)목마른 예배자 F
  +(후렴만)온 맘 다해 F`

const showTextImport = ref(false)
const textImportValue = ref('')
const textImportError = ref('')
const isImportingText = ref(false)

const handleTextImport = async () => {
  if (!textImportValue.value.trim()) {
    textImportError.value = '텍스트를 입력해주세요.'
    return
  }
  isImportingText.value = true
  textImportError.value = ''
  try {
    const created = await createSetlistFromText(textImportValue.value)
    showTextImport.value = false
    textImportValue.value = ''
    toast.success('콘티를 만들었어요')
    await router.push(`/setlists/${created.setlistId}`)
  } catch (e) {
    textImportError.value = apiError(e, '콘티 생성에 실패했습니다.')
  } finally {
    isImportingText.value = false
  }
}

const handleDelete = async (setlist: Setlist, event: Event) => {
  event.stopPropagation()
  const label = setlist.title ?? setlist.serviceDate
  if (!confirm(`"${label}" 콘티를 삭제할까요?`)) return
  try {
    await deleteSetlist(setlist.setlistId)
    await store.fetchSetlists()
  } catch (e) {
    alert(apiError(e, '삭제에 실패했습니다.'))
  }
}

const formatDate = (dateStr: string) => {
  const [y, m, d] = dateStr.split('-')
  return `${y}.${m}.${d}`
}

// ── 캘린더 뷰
const viewMode = ref<'list' | 'calendar'>('calendar')
const pad = (n: number) => String(n).padStart(2, '0')

const now = new Date()
const viewYear = ref(now.getFullYear())
const viewMonth = ref(now.getMonth() + 1) // 1~12
const selectedDate = ref<string | null>(null)

const monthLabel = computed(() => `${viewYear.value}년 ${viewMonth.value}월`)

const setlistsByDate = computed(() => {
  const map = new Map<string, Setlist[]>()
  for (const s of store.setlists) {
    const list = map.get(s.serviceDate) ?? []
    list.push(s)
    map.set(s.serviceDate, list)
  }
  return map
})

const calendarCells = computed(() => {
  const startDow = new Date(viewYear.value, viewMonth.value - 1, 1).getDay()
  const daysInMonth = new Date(viewYear.value, viewMonth.value, 0).getDate()
  const cells: ({ day: number; iso: string } | null)[] = []
  for (let i = 0; i < startDow; i++) cells.push(null)
  for (let d = 1; d <= daysInMonth; d++) {
    cells.push({ day: d, iso: `${viewYear.value}-${pad(viewMonth.value)}-${pad(d)}` })
  }
  return cells
})

const selectedDateSetlists = computed(() => selectedDate.value ? setlistsByDate.value.get(selectedDate.value) ?? [] : [])

// 캘린더 셀에 표시할 콘티 라벨 (제목 없으면 대체 텍스트) — #3
const setlistLabel = (s: Setlist) => s.title?.trim() || '제목 없음'

const prevMonth = () => {
  if (viewMonth.value === 1) { viewMonth.value = 12; viewYear.value-- } else { viewMonth.value-- }
}
const nextMonth = () => {
  if (viewMonth.value === 12) { viewMonth.value = 1; viewYear.value++ } else { viewMonth.value++ }
}

const selectDate = (iso: string) => {
  selectedDate.value = iso
  const matches = setlistsByDate.value.get(iso) ?? []
  if (matches.length === 1) {
    router.push(`/setlists/${matches[0].setlistId}`)
  }
}

onMounted(() => { void store.fetchSetlists() })
</script>

<template>
  <DefaultLayout>
    <SongPickerModal
      v-if="showSongPicker"
      :songs="songStore.songs"
      @select="onSongPicked"
      @close="showSongPicker = false"
    />

    <div class="flex items-center justify-between mb-6 gap-3">
      <h1 class="text-xl font-bold text-foreground shrink-0">콘티</h1>
      <div class="flex items-center gap-2">
        <div v-if="!showCreateForm" class="inline-flex rounded-md border border-border p-0.5">
          <button
            type="button"
            class="inline-flex items-center gap-1 px-2.5 h-7 rounded text-xs font-medium transition-colors"
            :class="viewMode === 'calendar' ? 'bg-primary text-primary-foreground' : 'text-muted-foreground hover:text-foreground'"
            @click="viewMode = 'calendar'"
          >
            <CalendarDays class="w-3.5 h-3.5" />
            캘린더
          </button>
          <button
            type="button"
            class="inline-flex items-center gap-1 px-2.5 h-7 rounded text-xs font-medium transition-colors"
            :class="viewMode === 'list' ? 'bg-primary text-primary-foreground' : 'text-muted-foreground hover:text-foreground'"
            @click="viewMode = 'list'"
          >
            <List class="w-3.5 h-3.5" />
            목록
          </button>
        </div>
        <Button
          variant="outline"
          @click="() => { showTextImport = !showTextImport; showCreateForm = false }"
        >
          <template v-if="showTextImport">
            <X class="w-4 h-4" />
            취소
          </template>
          <template v-else>
            <FileText class="w-4 h-4" />
            텍스트로 만들기
          </template>
        </Button>
        <Button
          :variant="showCreateForm ? 'outline' : 'default'"
          @click="() => { showCreateForm = !showCreateForm; showTextImport = false }"
        >
          <template v-if="showCreateForm">
            <X class="w-4 h-4" />
            취소
          </template>
          <template v-else>
            <Plus class="w-4 h-4" />
            새 콘티
          </template>
        </Button>
      </div>
    </div>

    <!-- 텍스트로 콘티 만들기 -->
    <Card v-if="showTextImport" class="p-5 mb-6 bg-muted/40">
      <h2 class="text-sm font-semibold text-foreground mb-2">텍스트로 콘티 만들기</h2>
      <p class="text-xs text-muted-foreground mb-3 whitespace-pre-line">{{ textImportHelp }}</p>
      <p v-if="textImportError" class="text-sm text-destructive bg-destructive-soft rounded-md px-3 py-2 mb-3 whitespace-pre-line">{{ textImportError }}</p>
      <Textarea
        v-model="textImportValue"
        rows="10"
        placeholder="여기에 콘티 텍스트를 붙여넣으세요"
        class="font-mono text-xs mb-3"
      />
      <Button :disabled="isImportingText" @click="handleTextImport">
        {{ isImportingText ? '만드는 중...' : '콘티 만들기' }}
      </Button>
    </Card>

    <!-- 생성 폼 -->
    <Card v-if="showCreateForm" class="p-5 mb-6 bg-muted/40">
      <div class="flex items-center gap-2 mb-4">
        <h2 class="text-sm font-semibold text-foreground">새 콘티 만들기</h2>
        <span v-if="formattedServiceDate" class="text-sm font-medium text-primary">{{ formattedServiceDate }}</span>
      </div>
      <p v-if="createError" class="text-sm text-destructive bg-destructive-soft rounded-md px-3 py-2 mb-4">{{ createError }}</p>
      <div class="flex flex-col gap-4">
        <DatePicker v-model="createForm.serviceDate" inline />
        <div class="flex flex-col gap-1.5">
          <Label for="setlist-title">제목</Label>
          <Input id="setlist-title" v-model="createForm.title" type="text" placeholder="선택사항" />
        </div>
        <!-- 곡 목록 -->
        <div class="flex flex-col gap-1.5">
          <div class="flex items-center justify-between">
            <Label>곡 목록</Label>
            <button
              type="button"
              class="inline-flex items-center gap-1 h-7 px-2.5 rounded-md border border-border text-xs font-medium text-foreground hover:bg-muted transition-colors"
              @click="openSongPicker"
            >
              <Plus class="w-3.5 h-3.5" />
              곡 추가
            </button>
          </div>
          <div v-if="pendingSongs.length === 0" class="text-xs text-muted-foreground py-2">
            추가된 곡이 없습니다.
          </div>
          <div v-else class="flex flex-col gap-1">
            <div
              v-for="(s, i) in pendingSongs"
              :key="i"
              class="flex items-center gap-2 px-3 py-1.5 rounded-md bg-muted/50"
            >
              <span class="text-xs text-muted-foreground w-5 shrink-0">{{ i + 1 }}</span>
              <Music class="w-3.5 h-3.5 text-muted-foreground shrink-0" />
              <span class="text-xs text-foreground flex-1 truncate">{{ s.title }}</span>
              <button type="button" class="p-0.5 text-muted-foreground hover:text-destructive" @click="removePendingSong(i)">
                <X class="w-3.5 h-3.5" />
              </button>
            </div>
          </div>
        </div>

        <div class="flex gap-2">
          <Button :disabled="isCreating" @click="handleCreate">
            {{ isCreating ? '생성 중...' : '생성' }}
          </Button>
        </div>
      </div>
    </Card>

    <!-- 등록 폼이 열려있으면 하단 목록은 숨김 (특히 모바일 화면 정리) -->
    <template v-if="!showCreateForm && !showTextImport">
    <p v-if="store.isLoading" class="text-sm text-muted-foreground py-8 text-center">불러오는 중...</p>
    <p v-else-if="store.errorMessage" class="text-sm text-destructive py-4">{{ store.errorMessage }}</p>

    <!-- 캘린더 뷰 -->
    <template v-else-if="viewMode === 'calendar'">
      <Card class="p-4 mb-4">
        <div class="flex items-center justify-between mb-3">
          <button type="button" class="p-1.5 rounded hover:bg-muted text-muted-foreground" @click="prevMonth">
            <ChevronLeft class="w-4 h-4" />
          </button>
          <span class="text-sm font-semibold text-foreground">{{ monthLabel }}</span>
          <button type="button" class="p-1.5 rounded hover:bg-muted text-muted-foreground" @click="nextMonth">
            <ChevronRight class="w-4 h-4" />
          </button>
        </div>

        <div class="grid grid-cols-7 gap-1 mb-1">
          <span
            v-for="(w, i) in ['일', '월', '화', '수', '목', '금', '토']"
            :key="w"
            class="text-center text-xs py-1"
            :class="i === 0 ? 'text-destructive' : i === 6 ? 'text-primary' : 'text-muted-foreground'"
          >{{ w }}</span>
        </div>

        <div class="grid grid-cols-7 gap-0.5 sm:gap-1">
          <template v-for="(cell, i) in calendarCells" :key="i">
            <span v-if="!cell" />
            <button
              v-else
              type="button"
              class="min-h-[3rem] sm:min-h-[3.75rem] rounded-md p-0.5 sm:p-1 flex flex-col items-stretch gap-0.5 text-left transition-colors overflow-hidden"
              :class="selectedDate === cell.iso
                ? 'ring-2 ring-primary ring-inset'
                : 'hover:bg-muted'"
              @click="selectDate(cell.iso)"
            >
              <span
                class="text-xs leading-none px-0.5"
                :class="setlistsByDate.has(cell.iso) ? 'font-semibold text-primary' : 'text-muted-foreground'"
              >{{ cell.day }}</span>
              <template v-if="setlistsByDate.get(cell.iso)">
                <span
                  v-for="s in setlistsByDate.get(cell.iso)!.slice(0, 2)"
                  :key="s.setlistId"
                  class="text-[9px] sm:text-[10px] leading-tight rounded px-0.5 sm:px-1 py-0.5 truncate bg-primary-soft text-primary"
                >{{ setlistLabel(s) }}</span>
                <span
                  v-if="setlistsByDate.get(cell.iso)!.length > 2"
                  class="text-[10px] leading-tight text-muted-foreground px-1"
                >+{{ setlistsByDate.get(cell.iso)!.length - 2 }}</span>
              </template>
            </button>
          </template>
        </div>
      </Card>

      <!-- 선택한 날짜의 콘티 목록 (같은 날짜에 여러 개인 경우) -->
      <div v-if="selectedDate" class="flex flex-col gap-2">
        <p v-if="selectedDateSetlists.length === 0" class="text-sm text-muted-foreground text-center py-6">
          {{ formatDate(selectedDate) }}에 등록된 콘티가 없습니다.
        </p>
        <div
          v-for="setlist in selectedDateSetlists"
          :key="setlist.setlistId"
          class="bg-card rounded-xl border border-border px-5 py-4 flex items-center justify-between gap-4 hover:border-primary/50 hover:shadow-md transition-all cursor-pointer group"
          @click="$router.push(`/setlists/${setlist.setlistId}`)"
        >
          <div class="flex items-center gap-3 min-w-0">
            <span class="text-sm font-semibold text-foreground flex-shrink-0">{{ formatDate(setlist.serviceDate) }}</span>
            <span v-if="setlist.title" class="text-sm text-muted-foreground truncate">{{ setlist.title }}</span>
          </div>
          <div class="flex items-center gap-3 flex-shrink-0">
            <span class="text-xs text-muted-foreground">{{ setlist.items.length }}곡</span>
            <ChevronRight class="w-4 h-4 text-muted-foreground group-hover:text-primary transition-colors" />
          </div>
        </div>
      </div>
    </template>

    <div v-else class="flex flex-col gap-5">
      <!-- 검색: 콘티 제목 / 날짜 / 포함된 곡 제목·아티스트 (#2) -->
      <div v-if="store.setlists.length > 0" class="relative">
        <Search class="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground pointer-events-none" />
        <Input
          v-model="searchQuery"
          type="search"
          placeholder="콘티·곡 제목, 아티스트, 날짜로 검색"
          class="pl-9"
        />
      </div>

      <div v-if="store.setlists.length === 0" class="py-16 flex flex-col items-center text-center">
        <div class="w-14 h-14 rounded-2xl bg-muted flex items-center justify-center mb-4">
          <Plus class="w-6 h-6 text-muted-foreground" />
        </div>
        <p class="text-sm font-medium text-foreground">콘티가 없습니다</p>
        <p class="text-sm text-muted-foreground mt-1">새 콘티를 만들어 예배 순서를 구성해보세요.</p>
      </div>

      <p v-else-if="!hasSearchResults" class="text-sm text-muted-foreground text-center py-12">
        '{{ searchQuery }}'에 해당하는 콘티가 없습니다.
      </p>

      <template v-else>
        <!-- 즐겨찾기 -->
        <div v-if="favoriteSetlists.length > 0" class="flex flex-col gap-2">
          <h2 class="text-xs font-semibold text-muted-foreground">즐겨찾기</h2>
          <div
            v-for="setlist in favoriteSetlists"
            :key="setlist.setlistId"
            class="bg-card rounded-xl border border-primary/30 px-4 py-3 sm:px-5 sm:py-4 flex items-center justify-between gap-3 sm:gap-4 hover:border-primary/50 hover:shadow-md transition-all cursor-pointer group"
            @click="$router.push(`/setlists/${setlist.setlistId}`)"
          >
            <div class="flex items-center gap-3 min-w-0">
              <span class="text-sm font-semibold text-foreground flex-shrink-0">{{ formatDate(setlist.serviceDate) }}</span>
              <span v-if="setlist.title" class="text-sm text-muted-foreground truncate">{{ setlist.title }}</span>
            </div>
            <div class="flex items-center gap-3 flex-shrink-0">
              <span class="text-xs text-muted-foreground">{{ setlist.items.length }}곡</span>
              <button
                type="button"
                class="text-primary"
                @click.stop="toggleFavorite(setlist.setlistId)"
              >
                <Star class="w-4 h-4 fill-current" />
              </button>
              <Button variant="destructive" size="sm" @click="handleDelete(setlist, $event)">
                <Trash2 class="w-3.5 h-3.5" />
              </Button>
              <ChevronRight class="w-4 h-4 text-muted-foreground group-hover:text-primary transition-colors" />
            </div>
          </div>
        </div>

        <!-- 전체 목록 -->
        <div class="flex flex-col gap-2">
          <h2 v-if="favoriteSetlists.length > 0" class="text-xs font-semibold text-muted-foreground">전체 콘티</h2>
          <div
            v-for="setlist in otherSetlists"
            :key="setlist.setlistId"
            class="bg-card rounded-xl border border-border px-4 py-3 sm:px-5 sm:py-4 flex items-center justify-between gap-3 sm:gap-4 hover:border-primary/50 hover:shadow-md transition-all cursor-pointer group"
            @click="$router.push(`/setlists/${setlist.setlistId}`)"
          >
            <div class="flex items-center gap-3 min-w-0">
              <span class="text-sm font-semibold text-foreground flex-shrink-0">{{ formatDate(setlist.serviceDate) }}</span>
              <span v-if="setlist.title" class="text-sm text-muted-foreground truncate">{{ setlist.title }}</span>
            </div>
            <div class="flex items-center gap-3 flex-shrink-0">
              <span class="text-xs text-muted-foreground">{{ setlist.items.length }}곡</span>
              <button
                type="button"
                class="text-muted-foreground hover:text-primary transition-colors"
                @click.stop="toggleFavorite(setlist.setlistId)"
              >
                <Star class="w-4 h-4" :class="{ 'fill-current text-primary': isFavorite(setlist.setlistId) }" />
              </button>
              <Button variant="destructive" size="sm" @click="handleDelete(setlist, $event)">
                <Trash2 class="w-3.5 h-3.5" />
              </Button>
              <ChevronRight class="w-4 h-4 text-muted-foreground group-hover:text-primary transition-colors" />
            </div>
          </div>
        </div>
      </template>
    </div>
    </template>
  </DefaultLayout>
</template>
