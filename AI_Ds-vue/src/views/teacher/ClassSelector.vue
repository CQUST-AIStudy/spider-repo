<template>
  <div class="class-selector-page">
    <div class="selector-card">
      <div class="selector-header">
        <h1>选择班级</h1>
        <p>请选择要管理的班级，后续所有数据将按班级隔离</p>
      </div>

      <div v-if="loading" style="text-align:center;padding:40px">
        <el-icon class="is-loading" :size="32"><Loading /></el-icon>
        <p style="color:#5f6368;margin-top:12px">加载班级列表...</p>
      </div>

      <div v-else class="class-grid">
        <div v-for="cls in classList" :key="cls.id || cls.name"
             class="class-item" :class="{ selected: selected === (cls.id || cls.name) }"
             @click="selected = cls.id || cls.name">
          <div class="class-icon">📚</div>
          <div class="class-info">
            <span class="class-name">{{ cls.name || cls.className || cls.class_name }}</span>
            <span class="class-meta">{{ cls.studentCount || cls.student_count || '—' }} 名学生</span>
          </div>
          <el-icon v-if="selected === (cls.id || cls.name)" class="check-icon"><CircleCheckFilled /></el-icon>
        </div>

        <!-- 如果没有班级数据，显示默认班级 -->
        <div v-if="classList.length === 0" class="class-item"
             :class="{ selected: selected === 'default' }"
             @click="selected = 'default'">
          <div class="class-icon">📚</div>
          <div class="class-info">
            <span class="class-name">{{ defaultClassName }}</span>
            <span class="class-meta">默认班级</span>
          </div>
          <el-icon v-if="selected === 'default'" class="check-icon"><CircleCheckFilled /></el-icon>
        </div>
      </div>

      <el-button type="primary" size="large" class="confirm-btn"
                 :disabled="!selected" @click="confirmSelect">
        进入班级
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../../store'
import { Loading, CircleCheckFilled } from '@element-plus/icons-vue'
import api from '../../api'

const router = useRouter()
const userStore = useUserStore()
const loading = ref(true)
const classList = ref([])
const selected = ref(null)

const defaultClassName = computed(() => userStore.userInfo?.classroom || '计科23')

onMounted(async () => {
  // 如果已经选了班级，直接跳转
  if (userStore.selectedClass) {
    router.replace('/teacher/dashboard')
    return
  }

  try {
    const res = await api.getClassList()
    const list = Array.isArray(res) ? res : (res?.data || [])
    classList.value = list
    // 如果只有一个班级，自动选中
    if (list.length === 1) {
      selected.value = list[0].id || list[0].name
    }
  } catch (e) {
    console.warn('获取班级列表失败，使用默认班级:', e)
  }
  // 如果没有班级数据，自动选中默认
  if (classList.value.length === 0) {
    selected.value = 'default'
  }
  loading.value = false
})

const confirmSelect = () => {
  if (!selected.value) return
  let cls
  if (selected.value === 'default') {
    cls = { id: 'default', name: defaultClassName.value }
  } else {
    const found = classList.value.find(c => (c.id || c.name) === selected.value)
    cls = found ? { id: found.id || found.name, name: found.name || found.className || found.class_name } : { id: selected.value, name: selected.value }
  }
  userStore.setSelectedClass(cls)
  router.replace('/teacher/dashboard')
}
</script>

<style scoped>
.class-selector-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f8f9fa;
}
.selector-card {
  width: 520px;
  background: #fff;
  border-radius: 16px;
  padding: 40px;
  box-shadow: 0 1px 3px rgba(60,64,67,0.15), 0 4px 8px rgba(60,64,67,0.08);
  border: 1px solid #dadce0;
}
.selector-header { text-align: center; margin-bottom: 28px; }
.selector-header h1 { font-size: 24px; font-weight: 400; color: #202124; margin: 0 0 8px; }
.selector-header p { font-size: 14px; color: #5f6368; margin: 0; }

.class-grid { display: flex; flex-direction: column; gap: 12px; margin-bottom: 24px; }
.class-item {
  display: flex; align-items: center; gap: 14px;
  padding: 16px 20px; border-radius: 12px;
  border: 2px solid #dadce0; cursor: pointer;
  transition: all 0.2s;
}
.class-item:hover { border-color: #bdc1c6; background: #f8f9fa; }
.class-item.selected { border-color: #1a73e8; background: #e8f0fe; }
.class-icon { font-size: 28px; }
.class-info { flex: 1; display: flex; flex-direction: column; }
.class-name { font-size: 16px; font-weight: 500; color: #202124; }
.class-meta { font-size: 13px; color: #5f6368; margin-top: 2px; }
.check-icon { font-size: 22px; color: #1a73e8; }

.confirm-btn {
  width: 100%; height: 46px; border-radius: 100px;
  font-size: 15px; font-weight: 500;
}
</style>
