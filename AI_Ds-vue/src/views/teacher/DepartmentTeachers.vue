<template>
  <div class="department-teachers">
    <page-header
      class="my-page-header"
      title="绯婚儴鏁欏笀"
      description="鏁欏笀绠＄悊鍜屾暀瀛︽暟鎹瑙?
    />

    <div class="teachers-content">
      <!-- 鏁欏笀缁熻淇℃伅 -->
      <el-card class="overview-card">
        <template #header>
          <div class="card-header"><span>鏁欏笀闃熶紞姒傚喌</span></div>
        </template>
        <el-row :gutter="20">
          <el-col :span="6">
            <div class="statistic-item">
              <div class="statistic-title">鏁欏笀鎬绘暟</div>
              <div class="statistic-value">{{ teachers.length }}</div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="statistic-item">
              <div class="statistic-title">绠＄悊鐝骇鏁?/div>
              <div class="statistic-value">{{ totalClasses }}</div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="statistic-item">
              <div class="statistic-title">瀛︾敓鎬绘暟</div>
              <div class="statistic-value">{{ totalStudents }}</div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="statistic-item">
              <div class="statistic-title">瀹為獙鎬绘暟</div>
              <div class="statistic-value">{{ totalExperiments }}</div>
            </div>
          </el-col>
        </el-row>
      </el-card>

      <!-- 鏁欏笀鍒楄〃 -->
      <el-card class="teachers-card">
        <template #header>
          <div class="card-header">
            <span>鏁欏笀鍒楄〃</span>
            <el-input
              v-model="searchQuery"
              placeholder="鎼滅储鏁欏笀濮撳悕"
              prefix-icon="Search"
              clearable
              style="width: 220px;"
            />
          </div>
        </template>

        <el-table :data="filteredTeachers" style="width: 100%" v-loading="loading" stripe>
          <el-table-column prop="name" label="濮撳悕" width="120" />
          <el-table-column prop="username" label="鐢ㄦ埛鍚? width="140" />
          <el-table-column label="绠＄悊鐝骇">
            <template #default="scope">
              <el-tag
                v-for="cls in scope.row.classes"
                :key="cls.id"
                class="course-tag"
                type="info"
                effect="plain"
              >{{ cls.name }}</el-tag>
              <span v-if="!scope.row.classes?.length" class="text-muted">鏆傛棤鐝骇</span>
            </template>
          </el-table-column>
          <el-table-column label="瀛︾敓鏁? width="100" align="center">
            <template #default="scope">
              {{ scope.row.studentCount || 0 }}
            </template>
          </el-table-column>
          <el-table-column label="鎿嶄綔" width="120">
            <template #default="scope">
              <el-button size="small" type="primary" link @click="viewTeacherClasses(scope.row)">鏌ョ湅鐝骇</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </div>

    <!-- 鏁欏笀鐝骇璇︽儏寮圭獥 -->
    <el-dialog v-model="dialogVisible" :title="'鏁欏笀鐝骇 - ' + selectedTeacher?.name" width="60%">
      <el-table :data="selectedTeacher?.classes || []" stripe>
        <el-table-column prop="name" label="鐝骇鍚嶇О" />
        <el-table-column prop="classCode" label="鐝骇浠ｇ爜" width="140" />
        <el-table-column prop="studentCount" label="瀛︾敓鏁? width="100" />
        <el-table-column prop="courseName" label="璇剧▼" width="140" />
        <el-table-column prop="grade" label="骞寸骇" width="100" />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import PageHeader from '../../components/PageHeader.vue'
import { ElMessage } from 'element-plus'
import api from '../../api'
import { getUserInfo } from '../../constants/auth'
const loading = ref(false)
const searchQuery = ref('')
const teachers = ref([])
const dialogVisible = ref(false)
const selectedTeacher = ref(null)
const totalExperiments = ref(0)

const filteredTeachers = computed(() => {
  if (!searchQuery.value) return teachers.value
  return teachers.value.filter(t => t.name?.includes(searchQuery.value) || t.username?.includes(searchQuery.value))
})

const totalClasses = computed(() => teachers.value.reduce((sum, t) => sum + (t.classes?.length || 0), 0))
const totalStudents = computed(() => teachers.value.reduce((sum, t) => sum + (t.studentCount || 0), 0))
const currentUserInfo = computed(() => getUserInfo() || {})

const fetchTeachers = async () => {
  loading.value = true
  try {
    // 浣跨敤 AI_Ds 鍚庣鐨勭彮绾ф帴鍙?
    const classesRes = await api.getClassList()
    const classList = Array.isArray(classesRes) ? classesRes : (classesRes?.data || [])

    // 鑾峰彇瀹為獙鏁版嵁鏉ヨˉ鍏呬俊鎭?
    let allStudentExperiments = []
    try {
      allStudentExperiments = await api.getAllStudentExperiments()
    } catch (e) {
      console.warn('鑾峰彇瀛︾敓瀹為獙鏁版嵁澶辫触:', e)
    }

    // 鎸夋暀甯堝垎缁?
    const teacherMap = new Map()
    classList.forEach(cls => {
      const teacherId = cls.teacherId || cls.createdBy || 'current'
      const teacherName = cls.teacherName || cls.createdByName || ''
      if (!teacherMap.has(teacherId)) {
        teacherMap.set(teacherId, {
          id: teacherId,
          name: teacherName || currentUserInfo.value.name || currentUserInfo.value.username || teacherId,
          username: cls.teacherUsername || teacherId,
          classes: [],
          studentCount: 0
        })
      }
      const teacher = teacherMap.get(teacherId)
      teacher.classes.push(cls)
      teacher.studentCount += cls.studentCount || 0
    })

    teachers.value = Array.from(teacherMap.values())

    // 濡傛灉娌℃湁浠庣彮绾т腑鎻愬彇鍒版暀甯堜俊鎭紝浣跨敤褰撳墠鐢ㄦ埛
    if (teachers.value.length === 0) {
      teachers.value = [{
        id: currentUserInfo.value.id || 1,
        name: currentUserInfo.value.name || currentUserInfo.value.username || 'teacher',
        username: currentUserInfo.value.username || '',
        classes: classList,
        studentCount: classList.reduce((sum, c) => sum + (c.studentCount || 0), 0)
      }]
    }
  } catch (e) {
    console.error('鑾峰彇鏁欏笀鏁版嵁澶辫触:', e)
    ElMessage.error('鑾峰彇鏁欏笀鏁版嵁澶辫触')
  } finally {
    loading.value = false
  }
}

const loadExperimentCount = async () => {
  try {
    const expRes = await api.getTeacherExperimentList()
    let exps = []
    if (expRes?.data && Array.isArray(expRes.data)) exps = expRes.data
    else if (Array.isArray(expRes)) exps = expRes
    totalExperiments.value = exps.length
  } catch (e) {
    console.error('鑾峰彇瀹為獙鏁伴噺澶辫触:', e)
  }
}

const viewTeacherClasses = (teacher) => {
  selectedTeacher.value = teacher
  dialogVisible.value = true
}

onMounted(() => {
  fetchTeachers()
  loadExperimentCount()
})
</script>

<style scoped>
.teachers-content { display: flex; flex-direction: column; gap: 20px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.statistic-item { text-align: center; padding: 20px 0; }
.statistic-title { font-size: 13px; color: #5f6368; }
.statistic-value { font-size: 28px; font-weight: 700; color: #202124; margin: 10px 0; }
.course-tag { margin-right: 5px; margin-bottom: 5px; }
.text-muted { color: #9aa0a6; font-size: 13px; }
.department-teachers :deep(.el-card) {
  border-radius: 16px;
  border: 1px solid #dadce0;
  box-shadow: 0 1px 3px rgba(0,0,0,0.04);
}
</style>




