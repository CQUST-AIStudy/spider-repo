<template>
  <div class="class-management">
    <page-header
        class="my-page-header"
        title="班级管理"
        description="管理系统中的班级信息"
    >
      <el-button type="primary" @click="showAddClassDialog">添加班级</el-button>
    </page-header>

    <el-card class="filter-card">
      <el-form :inline="true" :model="filterForm" class="filter-form">
        <el-form-item label="班级名称">
          <el-input v-model="filterForm.name" placeholder="输入班级名称" clearable/>
        </el-form-item>

        <el-form-item label="年级">
          <el-select v-model="filterForm.grade" placeholder="选择年级" clearable>
            <el-option label="全部" value=""/>
            <el-option label="2023级" value="2023"/>
            <el-option label="2022级" value="2022"/>
            <el-option label="2021级" value="2021"/>
            <el-option label="2020级" value="2020"/>
          </el-select>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="applyFilter">查询</el-button>
          <el-button @click="resetFilter">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card">
      <el-table :data="filteredClasses" style="width: 100%" border>
        <el-table-column prop="id" label="班级ID" width="120"/>
        <el-table-column prop="name" label="班级名称" min-width="180"/>
        <el-table-column prop="grade" label="年级" width="120"/>
        <el-table-column prop="studentCount" label="学生数量" width="120"/>
        <el-table-column prop="teacherName" label="班主任" width="150"/>
        <el-table-column label="操作" width="250">
          <template #default="scope">
            <el-button
                type="primary"
                link
                @click="editClass(scope.row)"
            >
              编辑
            </el-button>
            <el-button
                type="primary"
                link
                @click="manageStudents(scope.row)"
            >
              学生管理
            </el-button>
            <el-button
                type="danger"
                link
                @click="deleteClass(scope.row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 添加/编辑班级对话框 -->
    <el-dialog
        v-model="classDialogVisible"
        :title="dialogType === 'add' ? '添加班级' : '编辑班级'"
        width="500px"
    >
      <el-form ref="classFormRef" :model="classForm" :rules="classRules" label-width="100px">
        <el-form-item label="班级名称" prop="name">
          <el-input v-model="classForm.name" placeholder="例如：计算机科学与技术1班"/>
        </el-form-item>

        <el-form-item label="年级" prop="grade">
          <el-select v-model="classForm.grade" placeholder="选择年级" style="width: 100%">
            <el-option label="2023级" value="2023"/>
            <el-option label="2022级" value="2022"/>
            <el-option label="2021级" value="2021"/>
            <el-option label="2020级" value="2020"/>
          </el-select>
        </el-form-item>

        <el-form-item label="班主任" prop="teacherId">
          <el-select v-model="classForm.teacherId" placeholder="选择班主任" style="width: 100%">
            <el-option
                v-for="teacher in teacherOptions"
                :key="teacher.id"
                :label="teacher.name"
                :value="teacher.id"
            />
          </el-select>
        </el-form-item>
      </el-form>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="classDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="saveClass">确定</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import {ref, reactive, computed, onMounted} from 'vue'
import {useRouter} from 'vue-router'
import {ElMessage, ElMessageBox} from 'element-plus'
import PageHeader from '../../components/PageHeader.vue'

const router = useRouter()
const classes = ref([
  {
    id: 'C2023001',
    name: '计算机科学与技术1班',
    grade: '2023',
    studentCount: 49,
    teacherId: '20001',
    teacherName: '王老师'
  }
  // },
  // {
  //   id: 'C2023002',
  //   name: '计算机科学与技术2班',
  //   grade: '2023',
  //   studentCount: 45,
  //   teacherId: 'T2023002',
  //   teacherName: '王老师'
  // },
  // {
  //   id: 'C2022001',
  //   name: '软件工程1班',
  //   grade: '2022',
  //   studentCount: 38,
  //   teacherId: 'T2023003',
  //   teacherName: '张教授'
  // }
])

// 过滤表单
const filterForm = reactive({
  name: '',
  grade: ''
})

// 过滤后的班级列表
const filteredClasses = computed(() => {
  let result = [...classes.value]

  if (filterForm.name) {
    result = result.filter(cls => cls.name.includes(filterForm.name))
  }

  if (filterForm.grade) {
    result = result.filter(cls => cls.grade === filterForm.grade)
  }

  return result
})

// 教师选项
const teacherOptions = ref([
  {id: 'T2023001', name: '李教授'},
  {id: 'T2023002', name: '王老师'},
  {id: 'T2023003', name: '张教授'},
  {id: 'T2023004', name: '刘老师'}
])

// 班级表单
const classFormRef = ref(null)
const classForm = reactive({
  id: '',
  name: '',
  grade: '',
  teacherId: ''
})

// 表单验证规则
const classRules = {
  name: [
    {required: true, message: '请输入班级名称', trigger: 'blur'}
  ],
  grade: [
    {required: true, message: '请选择年级', trigger: 'change'}
  ],
  teacherId: [
    {required: true, message: '请选择班主任', trigger: 'change'}
  ]
}

// 对话框控制
const classDialogVisible = ref(false)
const dialogType = ref('add')
const currentClassId = ref('')

// 过滤
const applyFilter = () => {
  // 已在计算属性中处理
}

const resetFilter = () => {
  filterForm.name = ''
  filterForm.grade = ''
}

// 添加班级
const showAddClassDialog = () => {
  dialogType.value = 'add'
  classForm.id = ''
  classForm.name = ''
  classForm.grade = ''
  classForm.teacherId = ''
  classDialogVisible.value = true
}

// 编辑班级
const editClass = (cls) => {
  dialogType.value = 'edit'
  currentClassId.value = cls.id
  classForm.id = cls.id
  classForm.name = cls.name
  classForm.grade = cls.grade
  classForm.teacherId = cls.teacherId
  classDialogVisible.value = true
}

// 保存班级
const saveClass = () => {
  classFormRef.value.validate((valid) => {
    if (!valid) return

    if (dialogType.value === 'add') {
      // 模拟添加班级
      const newClass = {
        ...classForm,
        id: `C${classForm.grade}${Date.now().toString().slice(-3)}`,
        studentCount: 0,
        teacherName: teacherOptions.value.find(t => t.id === classForm.teacherId)?.name || ''
      }
      classes.value.unshift(newClass)
      ElMessage.success('添加班级成功')
    } else {
      // 模拟更新班级
      const index = classes.value.findIndex(c => c.id === currentClassId.value)
      if (index > -1) {
        const teacherName = teacherOptions.value.find(t => t.id === classForm.teacherId)?.name || ''
        classes.value[index] = {
          ...classes.value[index],
          name: classForm.name,
          grade: classForm.grade,
          teacherId: classForm.teacherId,
          teacherName: teacherName
        }
        ElMessage.success('更新班级成功')
      }
    }

    classDialogVisible.value = false
  })
}

// 管理学生
const manageStudents = (cls) => {
  router.push(`/admin/class-students/${cls.id}`)
}

// 删除班级
const deleteClass = (cls) => {
  ElMessageBox.confirm(
      `确定要删除班级 ${cls.name} 吗？此操作不可恢复。`,
      '警告',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
  ).then(() => {
    const index = classes.value.findIndex(c => c.id === cls.id)
    if (index > -1) {
      classes.value.splice(index, 1)
      ElMessage.success('删除班级成功')
    }
  }).catch(() => {
    // 取消删除
  })
}

// 加载教师选项
const loadTeacherOptions = async () => {
  try {
    // 实际应用中应通过API获取教师列表
    // const teachers = await api.getTeacherList()
    // teacherOptions.value = teachers
  } catch (error) {
    console.error('加载教师列表失败:', error)
  }
}

onMounted(() => {
  loadTeacherOptions()
})
</script>

<style scoped>
.class-management {
  height: 100%;
}

.filter-card,
.table-card {
  margin-bottom: 20px;
}

.filter-form {
  display: flex;
  flex-wrap: wrap;
}
.my-page-header {
  padding: 20px;
}

</style>
