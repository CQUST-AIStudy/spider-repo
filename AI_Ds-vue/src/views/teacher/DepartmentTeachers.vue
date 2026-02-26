<template>
  <div class="department-teachers">
    <page-header
      class="my-page-header"
      title="系部教师"
      description="教师管理和教学数据概览"
    />

    <div class="teachers-content">
      <!-- 教师统计信息 -->
      <el-card class="overview-card">
        <template #header>
          <div class="card-header"><span>教师队伍概况</span></div>
        </template>
        <el-row :gutter="20">
          <el-col :span="6">
            <div class="statistic-item">
              <div class="statistic-title">教师总数</div>
              <div class="statistic-value">{{ teachers.length }}</div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="statistic-item">
              <div class="statistic-title">管理班级数</div>
              <div class="statistic-value">{{ totalClasses }}</div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="statistic-item">
              <div class="statistic-title">学生总数</div>
              <div class="statistic-value">{{ totalStudents }}</div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="statistic-item">
              <div class="statistic-title">实验总数</div>
              <div class="statistic-value">{{ totalExperiments }}</div>
            </div>
          </el-col>
        </el-row>
      </el-card>

      <!-- 教师列表 -->
      <el-card class="teachers-card">
        <template #header>
          <div class="card-header">
            <span>教师列表</span>
            <el-input
              v-model="searchQuery"
              placeholder="搜索教师姓名"
              prefix-icon="Search"
              clearable
              style="width: 220px;"
            />
          </div>
        </template>

        <el-table :data="filteredTeachers" style="width: 100%" v-loading="loading" stripe>
          <el-table-column prop="name" label="姓名" width="120" />
          <el-table-column prop="username" label="用户名" width="140" />
          <el-table-column label="管理班级">
            <template #default="scope">
              <el-tag
                v-for="cls in scope.row.classes"
                :key="cls.id"
                class="course-tag"
                type="info"
                effect="plain"
              >{{ cls.name }}</el-tag>
              <span v-if="!scope.row.classes?.length" class="text-muted">暂无班级</span>
            </template>
          </el-table-column>
          <el-table-column label="学生数" width="100" align="center">
            <template #default="scope">
              {{ scope.row.studentCount || 0 }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="120">
            <template #default="scope">
              <el-button size="small" type="primary" link @click="viewTeacherClasses(scope.row)">查看班级</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </div>

    <!-- 教师班级详情弹窗 -->
    <el-dialog v-model="dialogVisible" :title="'教师班级 - ' + selectedTeacher?.name" width="60%">
      <el-table :data="selectedTeacher?.classes || []" stripe>
        <el-table-column prop="name" label="班级名称" />
        <el-table-column prop="classCode" label="班级代码" width="140" />
        <el-table-column prop="studentCount" label="学生数" width="100" />
        <el-table-column prop="courseName" label="课程" width="140" />
        <el-table-column prop="grade" label="年级" width="100" />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import PageHeader from '../../components/PageHeader.vue'
import { ElMessage } from 'element-plus'
import api from '../../api'
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

const fetchTeachers = async () => {
  loading.value = true
  try {
    // 使用 AI_Ds 后端的班级接口
    const classesRes = await api.getClassList()
    const classList = Array.isArray(classesRes) ? classesRes : (classesRes?.data || [])

    // 获取实验数据来补充信息
    let allStudentExperiments = []
    try {
      allStudentExperiments = await api.getAllStudentExperiments()
    } catch (e) {
      console.warn('获取学生实验数据失败:', e)
    }

    // 按教师分组
    const teacherMap = new Map()
    classList.forEach(cls => {
      const teacherId = cls.teacherId || cls.createdBy || 'current'
      const teacherName = cls.teacherName || cls.createdByName || ''
      if (!teacherMap.has(teacherId)) {
        const userInfo = JSON.parse(localStorage.getItem('userInfo') || '{}')
        teacherMap.set(teacherId, {
          id: teacherId,
          name: teacherName || userInfo.name || userInfo.username || '当前教师',
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

    // 如果没有从班级中提取到教师信息，使用当前用户
    if (teachers.value.length === 0) {
      const userInfo = JSON.parse(localStorage.getItem('userInfo') || '{}')
      teachers.value = [{
        id: userInfo.id || 1,
        name: userInfo.name || userInfo.username || '当前教师',
        username: userInfo.username || '',
        classes: classList,
        studentCount: classList.reduce((sum, c) => sum + (c.studentCount || 0), 0)
      }]
    }
  } catch (e) {
    console.error('获取教师数据失败:', e)
    ElMessage.error('获取教师数据失败')
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
    console.error('获取实验数量失败:', e)
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
