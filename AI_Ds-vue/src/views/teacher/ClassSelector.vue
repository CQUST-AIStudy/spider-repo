<template>
  <div class="class-selector-page">
    <div class="selector-card">
      <div class="selector-header">
        <h1>选择教学班</h1>
        <p>教师端按教学班隔离管理。先选择当前教学班，或先创建新的教学班。</p>
      </div>

      <div v-if="loading" class="loading-state">
        <el-icon class="is-loading" :size="30"><Loading /></el-icon>
        <p>正在加载教学班...</p>
      </div>

      <template v-else>
        <div v-if="classList.length" class="class-grid">
          <div
            v-for="cls in classList"
            :key="cls.id"
            class="class-item"
            :class="{ selected: selected === cls.id }"
            @click="selected = cls.id"
          >
            <div class="class-icon">班</div>
            <div class="class-info">
              <span class="class-name">{{ cls.name }}</span>
              <span class="class-meta">{{ cls.courseName || '未设置课程' }} · {{ cls.studentCount || 0 }} 人</span>
            </div>
            <el-icon v-if="selected === cls.id" class="check-icon"><CircleCheckFilled /></el-icon>
          </div>
        </div>

        <el-empty v-else description="你还没有创建任何教学班">
          <el-button type="primary" @click="goCreateClass">去创建教学班</el-button>
        </el-empty>
      </template>

      <div class="selector-actions">
        <el-button v-if="classList.length" type="primary" size="large" class="confirm-btn" :disabled="!selected" @click="confirmSelect">
          进入当前教学班
        </el-button>
        <el-button v-if="classList.length" size="large" class="secondary-btn" @click="goCreateClass">
          新建教学班
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { CircleCheckFilled, Loading } from '@element-plus/icons-vue'
import { useUserStore } from '../../store'
import { getTeachingClasses } from '../../api/tap'

const router = useRouter()
const userStore = useUserStore()
const loading = ref(true)
const classList = ref([])
const selected = ref(null)

onMounted(async () => {
  if (userStore.selectedClass) {
    await router.replace('/teacher/dashboard')
    return
  }

  try {
    const res = await getTeachingClasses()
    const list = res?.data || res || []
    classList.value = Array.isArray(list) ? list : []
    if (classList.value.length === 1) {
      selected.value = classList.value[0].id
    }
  } catch (error) {
    console.warn('加载教学班失败:', error)
    classList.value = []
  } finally {
    loading.value = false
  }
})

function confirmSelect() {
  const found = classList.value.find(item => item.id === selected.value)
  if (!found) return
  userStore.setSelectedClass({
    id: found.id,
    name: found.name,
    ptaKeyword: found.ptaKeyword || found.name
  })
  router.replace('/teacher/dashboard')
}

function goCreateClass() {
  router.push('/teacher/class-list')
}
</script>

<style scoped>
.class-selector-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: #f5f7fb;
}

.selector-card {
  width: 100%;
  max-width: 860px;
  padding: 36px;
  border-radius: 24px;
  background: #fff;
  border: 1px solid #e6eaf2;
  box-shadow: 0 18px 48px rgba(15, 23, 42, 0.08);
}

.selector-header {
  margin-bottom: 24px;
  text-align: center;
}

.selector-header h1 {
  margin: 0 0 8px;
  color: #1f2937;
  font-size: 28px;
}

.selector-header p {
  margin: 0;
  color: #6b7280;
}

.loading-state {
  padding: 40px 0;
  text-align: center;
  color: #6b7280;
}

.class-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 16px;
  margin-bottom: 24px;
}

.class-item {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  min-height: 112px;
  padding: 20px 20px 18px;
  border: 1px solid #dbe2ea;
  border-radius: 18px;
  cursor: pointer;
  transition: 0.2s ease;
}

.class-item:hover {
  border-color: #9fb3c8;
  background: #f8fbff;
}

.class-item.selected {
  border-color: #2563eb;
  background: #eff6ff;
}

.class-icon {
  width: 48px;
  height: 48px;
  display: grid;
  place-items: center;
  border-radius: 14px;
  background: #e0ecff;
  color: #1d4ed8;
  font-weight: 700;
  flex-shrink: 0;
}

.class-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.class-name {
  color: #111827;
  font-weight: 700;
  font-size: 20px;
  line-height: 1.35;
  word-break: break-word;
}

.class-meta {
  margin-top: 6px;
  color: #6b7280;
  font-size: 14px;
  line-height: 1.6;
  word-break: break-word;
}

.check-icon {
  color: #2563eb;
  font-size: 20px;
}

.selector-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.confirm-btn,
.secondary-btn {
  flex: 1;
  border-radius: 12px;
}

@media (max-width: 768px) {
  .selector-card {
    padding: 24px;
  }

  .class-grid {
    grid-template-columns: 1fr;
  }
}
</style>
