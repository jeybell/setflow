<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Plus, Music, Search, X, FolderUp, Loader2, ChevronDown, GitMerge, CheckSquare } from '@lucide/vue'
import SongList from '../components/SongList.vue'
import Select from '../components/ui/Select.vue'
import Button from '../components/ui/Button.vue'
import DefaultLayout from '../layouts/DefaultLayout.vue'
import { useSongStore } from '../stores/songStore'
import { getAllTags, mergeSongs } from '../apis/songApi'
import { useToast } from '../composables/useToast'
import { extractApiError } from '../composables/useApiError'

const songStore = useSongStore()
const toast = useToast()

// ── 곡 합치기(병합) 선택 모드 ──────────────────────────────
const selectMode = ref(false)
const selectedIds = ref<number[]>([])
const showMergeModal = ref(false)
const mergeTargetId = ref<number | null>(null)
const isMerging = ref(false)

const selectedSongs = computed(() =>
  songStore.listSongs.filter((s) => selectedIds.value.includes(s.songId)),
)

const toggleSelectMode = () => {
  selectMode.value = !selectMode.value
  selectedIds.value = []
}

const toggleSelect = (songId: number) => {
  const idx = selectedIds.value.indexOf(songId)
  if (idx === -1) selectedIds.value.push(songId)
  else selectedIds.value.splice(idx, 1)
}

const openMergeModal = () => {
  // 기본 대상: 선택한 곡 중 가장 위(먼저 선택 목록에 있는) 곡
  mergeTargetId.value = selectedIds.value[0] ?? null
  showMergeModal.value = true
}

const confirmMerge = async () => {
  if (mergeTargetId.value == null || isMerging.value) return
  const target = mergeTargetId.value
  const sources = selectedIds.value.filter((id) => id !== target)
  if (sources.length === 0) return
  isMerging.value = true
  try {
    const res = await mergeSongs(target, sources)
    showMergeModal.value = false
    selectMode.value = false
    selectedIds.value = []
    toast.success(`${res.mergedSongCount}곡을 합쳤어요 (악보 ${res.movedSheetCount}개 이관)`)
    runSearch()
    allTags.value = await getAllTags()
  } catch (e) {
    toast.error(extractApiError(e, '곡 합치기에 실패했습니다.'))
  } finally {
    isMerging.value = false
  }
}

const query = ref('')
const songKey = ref('')
const selectedTags = ref<string[]>([])
const allTags = ref<string[]>([])
const hasFilter = ref(false)

const SORT_OPTIONS = [
  { value: 'TITLE', label: '이름순' },
  { value: 'ARTIST', label: '아티스트순' },
  { value: 'KEY', label: '키순' },
  { value: 'LATEST', label: '최신순' },
  { value: 'LAST_USED', label: '최근 사용일순' },
] as const
const SORT_STORAGE_KEY = 'song-list-sort'
const sortBy = ref<string>(localStorage.getItem(SORT_STORAGE_KEY) ?? 'TITLE')

const sentinel = ref<HTMLElement | null>(null)
let observer: IntersectionObserver | undefined

// 태그 칩: 기본 2줄까지만 보이고 넘치면 '더보기'로 펼침 (#146)
const tagsExpanded = ref(false)
const tagsOverflow = ref(false)
const tagWrapEl = ref<HTMLElement | null>(null)
const COLLAPSED_TAG_PX = 64 // max-h-16 = 약 2줄
let tagResizeObserver: ResizeObserver | undefined

const checkTagOverflow = () => {
  const el = tagWrapEl.value
  tagsOverflow.value = !!el && el.scrollHeight > COLLAPSED_TAG_PX
}

// 태그 영역은 allTags 로드 후에야 렌더되므로 ref 변화를 감지해 관찰을 건다.
watch(tagWrapEl, (el) => {
  if (tagResizeObserver) { tagResizeObserver.disconnect(); tagResizeObserver = undefined }
  if (!el) return
  tagResizeObserver = new ResizeObserver(() => checkTagOverflow())
  tagResizeObserver.observe(el)
  checkTagOverflow()
})

let debounceTimer: ReturnType<typeof setTimeout> | undefined

const runSearch = () => {
  hasFilter.value = Boolean(query.value.trim() || songKey.value.trim() || selectedTags.value.length)
  void songStore.fetchFirstPage({
    query: query.value,
    songKey: songKey.value,
    tags: selectedTags.value,
    sort: sortBy.value,
  })
}

watch(sortBy, (value) => {
  localStorage.setItem(SORT_STORAGE_KEY, value)
  runSearch()
})

watch([query, songKey], () => {
  clearTimeout(debounceTimer)
  debounceTimer = setTimeout(runSearch, 300)
})

watch(selectedTags, () => {
  runSearch()
}, { deep: true })

const toggleTag = (tag: string) => {
  const idx = selectedTags.value.indexOf(tag)
  if (idx === -1) selectedTags.value.push(tag)
  else selectedTags.value.splice(idx, 1)
}

const clearTags = () => {
  selectedTags.value = []
}

const clearFilters = () => {
  query.value = ''
  songKey.value = ''
  selectedTags.value = []
}

// sentinel 은 목록이 렌더된 뒤에야 존재하므로 ref 변화를 감지해 관찰을 건다.
watch(sentinel, (el, prev) => {
  if (prev && observer) observer.unobserve(prev)
  if (!el) return
  if (!observer) {
    observer = new IntersectionObserver((entries) => {
      if (entries[0]?.isIntersecting) void songStore.fetchNextPage()
    }, { rootMargin: '200px' })
  }
  observer.observe(el)
})

onMounted(async () => {
  runSearch()
  allTags.value = await getAllTags()
})

onBeforeUnmount(() => {
  observer?.disconnect()
  tagResizeObserver?.disconnect()
})
</script>

<template>
  <DefaultLayout>
    <div class="sticky top-12 sm:top-14 z-30 bg-background -mx-3 sm:-mx-6 px-3 sm:px-6 pt-4 sm:pt-8 -mt-4 sm:-mt-8 pb-3">
    <div class="flex items-center justify-between mb-5">
      <h1 class="text-xl font-bold text-foreground">악보</h1>
      <div class="flex items-center gap-2">
        <button
          type="button"
          :aria-label="selectMode ? '선택 취소' : '곡 합치기'"
          class="inline-flex items-center gap-1.5 h-9 px-3 sm:px-4 rounded-md border text-sm font-medium whitespace-nowrap transition-colors"
          :class="selectMode
            ? 'border-primary bg-primary-soft text-primary'
            : 'border-border bg-card text-foreground hover:bg-muted'"
          @click="toggleSelectMode"
        >
          <CheckSquare class="w-4 h-4 shrink-0" />
          <span class="hidden sm:inline">{{ selectMode ? '선택 취소' : '곡 합치기' }}</span>
        </button>
        <RouterLink
          v-if="!selectMode"
          to="/songs/bulk"
          aria-label="일괄 업로드"
          class="inline-flex items-center gap-1.5 h-9 px-3 sm:px-4 rounded-md border border-border bg-card text-sm font-medium text-foreground whitespace-nowrap hover:bg-muted transition-colors"
        >
          <FolderUp class="w-4 h-4 shrink-0" />
          <span class="hidden sm:inline">일괄 업로드</span>
        </RouterLink>
        <RouterLink
          v-if="!selectMode"
          to="/songs/new"
          aria-label="곡 등록"
          class="inline-flex items-center gap-1.5 h-9 px-3 sm:px-4 rounded-md bg-primary text-primary-foreground text-sm font-medium whitespace-nowrap hover:opacity-90 transition-opacity"
        >
          <Plus class="w-4 h-4 shrink-0" />
          <span class="hidden sm:inline">곡 등록</span>
        </RouterLink>
      </div>
    </div>

    <!-- 선택 모드 안내 -->
    <div v-if="selectMode" class="mb-4 rounded-lg border border-primary/30 bg-primary-soft/30 px-4 py-2.5 text-sm text-foreground">
      합칠 곡들을 선택하세요. 선택한 곡을 하나로 합치고, 남길 곡을 다음 단계에서 고릅니다.
    </div>

    <!-- 검색 영역 -->
    <div class="flex flex-col sm:flex-row gap-2 mb-6">
      <div class="relative flex-1">
        <Search class="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
        <input
          v-model="query"
          type="text"
          placeholder="제목 · 아티스트 · 가사 · 태그 검색"
          class="w-full h-10 pl-9 pr-9 rounded-md border border-input bg-card text-sm text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-ring focus:border-transparent"
        />
        <button
          v-if="query"
          type="button"
          aria-label="검색어 지우기"
          class="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
          @click="query = ''"
        >
          <X class="w-4 h-4" />
        </button>
      </div>
      <input
        v-model="songKey"
        type="text"
        placeholder="키 (예: C, G, Am)"
        class="h-10 w-full sm:w-40 px-3 rounded-md border border-input bg-card text-sm text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-ring focus:border-transparent"
      />
    </div>

    <!-- 태그 필터 (다중 선택, AND) — 줄바꿈 + 기본 2줄 접기/더보기, 해제 버튼 고정 노출 -->
    <div v-if="allTags.length > 0" class="mb-5">
      <div
        ref="tagWrapEl"
        class="flex flex-wrap items-center gap-1.5"
        :class="{ 'max-h-16 overflow-hidden': !tagsExpanded && tagsOverflow }"
      >
        <button
          v-for="tag in allTags"
          :key="tag"
          type="button"
          class="inline-flex items-center h-7 px-2.5 rounded-full text-xs font-medium border transition-colors"
          :class="selectedTags.includes(tag)
            ? 'bg-primary text-primary-foreground border-primary'
            : 'bg-card text-muted-foreground border-border hover:border-primary hover:text-primary'"
          @click="toggleTag(tag)"
        >
          {{ tag }}
        </button>
      </div>
      <div v-if="tagsOverflow || selectedTags.length > 0" class="flex items-center gap-3 mt-2">
        <button
          v-if="tagsOverflow"
          type="button"
          class="inline-flex items-center gap-1 text-xs font-medium text-muted-foreground hover:text-foreground transition-colors"
          @click="tagsExpanded = !tagsExpanded"
        >
          <ChevronDown class="w-3.5 h-3.5 transition-transform" :class="{ 'rotate-180': tagsExpanded }" />
          {{ tagsExpanded ? '태그 접기' : '태그 더보기' }}
        </button>
        <button
          v-if="selectedTags.length > 0"
          type="button"
          class="inline-flex items-center gap-1 text-xs font-medium text-muted-foreground hover:text-foreground transition-colors ml-auto"
          @click="clearTags"
        >
          <X class="w-3 h-3" />
          태그 {{ selectedTags.length }}개 해제
        </button>
      </div>
    </div>

    <!-- 결과 건수 + 정렬 -->
    <div class="flex items-center justify-between gap-2 mb-3">
      <p class="text-xs text-muted-foreground">
        <template v-if="songStore.hasListSongs">
          {{ hasFilter ? '검색 결과' : '전체' }} 총 <span class="font-medium text-foreground">{{ songStore.listTotal }}</span>곡
        </template>
      </p>
      <div class="flex items-center gap-1.5 shrink-0">
        <span class="text-xs text-muted-foreground">정렬</span>
        <Select v-model="sortBy" class="!h-8 !w-28 text-xs">
          <option v-for="opt in SORT_OPTIONS" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
        </Select>
      </div>
    </div>
    </div>

    <p v-if="songStore.isLoadingList" class="text-sm text-muted-foreground py-8 text-center">불러오는 중...</p>
    <p v-else-if="songStore.errorMessage" class="text-sm text-destructive py-4">{{ songStore.errorMessage }}</p>

    <!-- 검색 결과 없음 -->
    <div v-else-if="!songStore.hasListSongs && hasFilter" class="py-16 flex flex-col items-center text-center">
      <div class="w-14 h-14 rounded-2xl bg-muted flex items-center justify-center mb-4">
        <Search class="w-6 h-6 text-muted-foreground" />
      </div>
      <p class="text-sm font-medium text-foreground">검색 결과가 없습니다</p>
      <p class="text-sm text-muted-foreground mt-1">다른 검색어나 키로 다시 시도해보세요.</p>
      <button
        type="button"
        class="mt-4 inline-flex items-center gap-1.5 h-9 px-4 rounded-md border border-border text-sm font-medium text-foreground hover:bg-muted transition-colors"
        @click="clearFilters"
      >
        <X class="w-4 h-4" />
        필터 초기화
      </button>
    </div>

    <!-- 곡 없음 -->
    <div v-else-if="!songStore.hasListSongs" class="py-16 flex flex-col items-center text-center">
      <div class="w-14 h-14 rounded-2xl bg-muted flex items-center justify-center mb-4">
        <Music class="w-6 h-6 text-muted-foreground" />
      </div>
      <p class="text-sm font-medium text-foreground">등록된 곡이 없습니다</p>
      <p class="text-sm text-muted-foreground mt-1">첫 번째 곡을 등록해 악보를 정리해보세요.</p>
      <RouterLink
        to="/songs/new"
        class="mt-4 inline-flex items-center gap-1.5 h-9 px-4 rounded-md bg-primary text-primary-foreground text-sm font-medium hover:opacity-90 transition-opacity"
      >
        <Plus class="w-4 h-4" />
        곡 등록
      </RouterLink>
    </div>

    <template v-else>
      <SongList
        :songs="songStore.listSongs"
        :selectable="selectMode"
        :selected-ids="selectedIds"
        @toggle="toggleSelect"
      />

      <!-- 무한 스크롤 감지 지점 -->
      <div ref="sentinel" class="h-4" />
      <div v-if="songStore.isLoadingMore" class="flex items-center justify-center gap-2 py-4 text-sm text-muted-foreground">
        <Loader2 class="w-4 h-4 animate-spin" />
        더 불러오는 중...
      </div>
    </template>

    <!-- 선택 모드 하단 액션 바 -->
    <div
      v-if="selectMode"
      class="fixed inset-x-0 bottom-0 z-40 border-t border-border bg-background/95 backdrop-blur px-4 py-3"
    >
      <div class="max-w-5xl mx-auto flex items-center gap-3">
        <span class="text-sm text-foreground"><span class="font-semibold text-primary">{{ selectedIds.length }}</span>곡 선택됨</span>
        <button
          v-if="selectedIds.length > 0"
          type="button"
          class="text-xs text-muted-foreground hover:text-foreground"
          @click="selectedIds = []"
        >선택 해제</button>
        <Button class="ml-auto" :disabled="selectedIds.length < 2" @click="openMergeModal">
          <GitMerge class="w-4 h-4" />
          합치기 ({{ selectedIds.length }})
        </Button>
      </div>
    </div>

    <!-- 병합 대상 선택 모달 -->
    <div
      v-if="showMergeModal"
      class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 px-4"
      @click.self="showMergeModal = false"
    >
      <div class="w-full max-w-md rounded-xl bg-card border border-border shadow-lg p-5 max-h-[85vh] flex flex-col">
        <h2 class="text-base font-semibold text-foreground mb-1">곡 합치기</h2>
        <p class="text-sm text-muted-foreground mb-4">
          <span class="font-medium text-foreground">남길 곡</span>을 하나 고르세요. 나머지 곡의 악보·콘티 참조가 이 곡으로 합쳐지고, 나머지 곡은 삭제됩니다.
        </p>
        <div class="flex flex-col gap-1.5 overflow-y-auto -mx-1 px-1">
          <label
            v-for="song in selectedSongs"
            :key="song.songId"
            class="flex items-center gap-3 rounded-lg border px-3 py-2.5 cursor-pointer transition-colors"
            :class="mergeTargetId === song.songId ? 'border-primary bg-primary-soft/30' : 'border-border hover:bg-muted'"
          >
            <input
              type="radio"
              name="merge-target"
              class="accent-primary"
              :value="song.songId"
              :checked="mergeTargetId === song.songId"
              @change="mergeTargetId = song.songId"
            />
            <div class="min-w-0">
              <p class="text-sm font-medium text-foreground truncate">{{ song.title }}</p>
              <p class="text-xs text-muted-foreground truncate">{{ song.artist ?? '아티스트 미상' }}</p>
            </div>
          </label>
        </div>
        <div class="flex gap-2 mt-5">
          <Button :disabled="mergeTargetId == null || isMerging" @click="confirmMerge">
            {{ isMerging ? '합치는 중...' : '이 곡으로 합치기' }}
          </Button>
          <Button variant="outline" :disabled="isMerging" @click="showMergeModal = false">취소</Button>
        </div>
      </div>
    </div>
  </DefaultLayout>
</template>
