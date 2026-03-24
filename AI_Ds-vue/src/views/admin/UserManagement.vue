<template>
  <div class="user-management">
    <page-header
        class="my-page-header"
      title="用户管理"
      description="管理系统用户，包括学生、教师和管理员"
    >
      <el-button type="primary" :disabled="!userManagementReady" @click="showAddUserDialog">添加用户</el-button>
    </page-header>

    <el-alert
      v-if="!userManagementReady"
      class="read-only-alert"
      type="warning"
      :closable="false"
      title="当前用户管理页仍是前端样例数据"
      description="后端还没有 /api/users 这组真实接口，新增、编辑、重置密码、删除和启停状态暂时禁用，避免页面显示成功但数据实际上没有落库。"
      show-icon
    />

    <el-card class="filter-card">
      <el-form :inline="true" :model="filterForm" class="filter-form">
        <el-form-item label="用户ID">
          <el-input v-model="filterForm.id" placeholder="输入用户ID" clearable />
        </el-form-item>

        <el-form-item label="用户名">
          <el-input v-model="filterForm.name" placeholder="输入用户名" clearable />
        </el-form-item>

        <el-form-item label="角色">
          <el-select v-model="filterForm.role" placeholder="选择角色" clearable>
            <el-option label="全部" value="" />
            <el-option label="学生" value="student" />
            <el-option label="教师" value="teacher" />
            <el-option label="管理员" value="admin" />
          </el-select>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="applyFilter">查询</el-button>
          <el-button @click="resetFilter">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card">
      <el-table :data="filteredUsers" style="width: 100%" border>
        <el-table-column prop="id" label="用户ID" width="120" />
        <el-table-column label="用户信息" min-width="200">
          <template #default="scope">
            <div class="user-info">
              <el-avatar :size="32" :src="scope.row.avatar" />
              <div class="user-details">
                <div class="user-name">{{ scope.row.name }}</div>
                <div class="user-extra">{{ scope.row.role === 'student' ? scope.row.class : scope.row.department }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="email" label="邮箱" min-width="180" />
        <el-table-column prop="phone" label="电话" width="150" />
        <el-table-column label="角色" width="100">
          <template #default="scope">
            <el-tag :type="getRoleType(scope.row.role)">
              {{ getRoleText(scope.row.role) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="scope">
            <el-switch
              v-model="scope.row.status"
              :active-value="'active'"
              :inactive-value="'inactive'"
              :disabled="!userManagementReady"
              @change="handleStatusChange(scope.row)"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180">
          <template #default="scope">
            <el-button type="primary" link :disabled="!userManagementReady" @click="editUser(scope.row)">编辑</el-button>
            <el-button type="primary" link :disabled="!userManagementReady" @click="resetPassword(scope.row)">重置密码</el-button>
            <el-button type="danger" link :disabled="!userManagementReady" @click="deleteUser(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-container">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next, jumper"
          :total="totalUsers"
          :page-size="pageSize"
          :current-page="currentPage"
          :page-sizes="[10, 20, 50, 100]"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </el-card>

    <!-- 添加/编辑用户对话框 -->
    <el-dialog
      v-model="userDialogVisible"
      :title="dialogType === 'add' ? '添加用户' : '编辑用户'"
      width="500px"
    >
      <el-form ref="userFormRef" :model="userForm" :rules="userRules" label-width="100px">
        <el-form-item label="用户名" prop="name">
          <el-input v-model="userForm.name" />
        </el-form-item>

        <el-form-item label="角色" prop="role">
          <el-select v-model="userForm.role" placeholder="选择角色" style="width: 100%">
            <el-option label="学生" value="student" />
            <el-option label="教师" value="teacher" />
            <el-option label="管理员" value="admin" />
          </el-select>
        </el-form-item>

        <el-form-item label="邮箱" prop="email">
          <el-input v-model="userForm.email" />
        </el-form-item>

        <el-form-item label="电话" prop="phone">
          <el-input v-model="userForm.phone" />
        </el-form-item>

        <template v-if="userForm.role === 'student'">
          <el-form-item label="班级" prop="class">
            <el-select v-model="userForm.class" placeholder="选择班级" style="width: 100%">
              <el-option
                v-for="item in classList"
                :key="item.id"
                :label="item.name"
                :value="item.name"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="年级" prop="grade">
            <el-input v-model="userForm.grade" />
          </el-form-item>
        </template>

        <template v-else>
          <el-form-item label="部门" prop="department">
            <el-input v-model="userForm.department" />
          </el-form-item>

          <template v-if="userForm.role === 'teacher'">
            <el-form-item label="职称" prop="title">
              <el-input v-model="userForm.title" />
            </el-form-item>
          </template>
        </template>

        <el-form-item v-if="dialogType === 'add'" label="密码" prop="password">
          <el-input v-model="userForm.password" type="password" show-password />
        </el-form-item>
      </el-form>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="userDialogVisible = false">取消</el-button>
          <el-button type="primary" :disabled="!userManagementReady" @click="saveUser">确定</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 重置密码对话框 -->
    <el-dialog v-model="resetPasswordDialogVisible" title="重置密码" width="400px">
      <el-form ref="resetPasswordFormRef" :model="resetPasswordForm" label-width="100px">
        <el-form-item label="新密码" prop="password">
          <el-input v-model="resetPasswordForm.password" type="password" show-password />
        </el-form-item>

        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input v-model="resetPasswordForm.confirmPassword" type="password" show-password />
        </el-form-item>
      </el-form>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="resetPasswordDialogVisible = false">取消</el-button>
          <el-button type="primary" :disabled="!userManagementReady" @click="confirmResetPassword">确定</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '../../components/PageHeader.vue'
import api from '../../api'

const userManagementReady = false
const showReadOnlyNotice = () => {
  ElMessage.warning('用户管理真实后端尚未接通，当前页面仅保留只读展示。')
}

// 表格数据
const users = ref([
  {
    id: '2019443672',
    name: '易星贵',
    avatar: 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png',
    class: '计算机科学与技术1班',
    grade: '2023级',
    email: 'student1@example.com',
    phone: '暂无',
    role: 'student',
    status: 'active'
  },
  {
    id: '2023442308',
    name: '施鉴航',
    avatar: 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png',
    class: '计算机科学与技术1班',
    grade: '2023级',
    email: 'student1@example.com',
    phone: '暂无',
    role: 'student',
    status: 'active'
  },
  {
    id: '2023440548',
    name: '李京谕',
    avatar: 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png',
    class: '计算机科学与技术1班',
    grade: '2023级',
    email: 'student1@example.com',
    phone: '暂无',
    role: 'student',
    status: 'active'
  },

  {
    id: '20001',
    name: '王老师',
    avatar: 'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png',
    department: '计算机科学与工程学院',
    title: '副教授',
    email: 'liteacher@example.com',
    phone: '13800138000',
    role: 'teacher',
    status: 'active'
  },
  {
    id: 'A2023001',
    name: '王管理',
    avatar: 'https://cube.elemecdn.com/9/c2/f0ee8a3c7c9638a54940382568c9cpng.png',
    department: '教务处',
    email: 'admin@example.com',
    phone: '13900139000',
    role: 'admin',
    status: 'active'
  }
])

// 班级列表
const classList = ref([])

// 过滤表单
const filterForm = reactive({
  id: '',
  name: '',
  role: ''
})

// 用户表单
const userFormRef = ref(null)
const userForm = reactive({
  id: '',
  name: '',
  role: 'student',
  email: '',
  phone: '',
  class: '',
  grade: '',
  department: '',
  title: '',
  password: ''
})

// 表单验证规则
const userRules = {
  name: [
    { required: true, message: '请输入用户名', trigger: 'blur' }
  ],
  role: [
    { required: true, message: '请选择角色', trigger: 'change' }
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于6位', trigger: 'blur' }
  ]
}

// 重置密码表单
const resetPasswordFormRef = ref(null)
const resetPasswordForm = reactive({
  userId: '',
  password: '',
  confirmPassword: ''
})

// 对话框控制
const userDialogVisible = ref(false)
const resetPasswordDialogVisible = ref(false)
const dialogType = ref('add')
const currentUserId = ref('')

// 分页相关
const pageSize = ref(10)
const currentPage = ref(1)
const totalUsers = computed(() => filteredUsers.value.length)

// 过滤用户列表
const filteredUsers = computed(() => {
  let result = [...users.value]

  if (filterForm.id) {
    result = result.filter(user => user.id.includes(filterForm.id))
  }

  if (filterForm.name) {
    result = result.filter(user => user.name.includes(filterForm.name))
  }

  if (filterForm.role) {
    result = result.filter(user => user.role === filterForm.role)
  }

  return result
})

// 加载班级列表
const loadClassList = async () => {
  try {
    const classes = await api.getClassList()
    classList.value = classes
  } catch (error) {
    console.error('加载班级列表失败:', error)
  }
}

// 获取角色类型和文本
const getRoleType = (role) => {
  const typeMap = {
    'student': 'info',
    'teacher': 'success',
    'admin': 'danger'
  }
  return typeMap[role] || 'info'
}

const getRoleText = (role) => {
  const textMap = {
    'student': '学生',
    'teacher': '教师',
    'admin': '管理员'
  }
  return textMap[role] || '未知'
}

// 过滤
const applyFilter = () => {
  currentPage.value = 1
}

const resetFilter = () => {
  filterForm.id = ''
  filterForm.name = ''
  filterForm.role = ''
  currentPage.value = 1
}

// 分页
const handleSizeChange = (size) => {
  pageSize.value = size
}

const handleCurrentChange = (page) => {
  currentPage.value = page
}

// 状态更改
const handleStatusChange = (user) => {
  if (!userManagementReady) {
    user.status = user.status === 'active' ? 'inactive' : 'active'
    showReadOnlyNotice()
    return
  }
  const status = user.status === 'active' ? '启用' : '禁用'
  ElMessage.success(`已${status}用户 ${user.name}`)
}

// 添加用户
const showAddUserDialog = () => {
  if (!userManagementReady) {
    showReadOnlyNotice()
    return
  }
  dialogType.value = 'add'
  userForm.id = ''
  userForm.name = ''
  userForm.role = 'student'
  userForm.email = ''
  userForm.phone = ''
  userForm.class = ''
  userForm.grade = ''
  userForm.department = ''
  userForm.title = ''
  userForm.password = ''
  userDialogVisible.value = true
}

// 编辑用户
const editUser = (user) => {
  if (!userManagementReady) {
    showReadOnlyNotice()
    return
  }
  dialogType.value = 'edit'
  currentUserId.value = user.id
  userForm.id = user.id
  userForm.name = user.name
  userForm.role = user.role
  userForm.email = user.email
  userForm.phone = user.phone
  userForm.class = user.class || ''
  userForm.grade = user.grade || ''
  userForm.department = user.department || ''
  userForm.title = user.title || ''
  userForm.password = ''
  userDialogVisible.value = true
}

// 保存用户
const saveUser = () => {
  if (!userManagementReady) {
    showReadOnlyNotice()
    return
  }
  userFormRef.value.validate((valid) => {
    if (!valid) return

    if (dialogType.value === 'add') {
      // 模拟添加用户
      const newUser = {
        ...userForm,
        id: `${userForm.role.charAt(0).toUpperCase()}${Date.now().toString().slice(-7)}`,
        avatar: 'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png',
        status: 'active'
      }
      users.value.unshift(newUser)
      ElMessage.success('添加用户成功')
    } else {
      // 模拟更新用户
      const index = users.value.findIndex(u => u.id === currentUserId.value)
      if (index > -1) {
        users.value[index] = {
          ...users.value[index],
          name: userForm.name,
          email: userForm.email,
          phone: userForm.phone,
          role: userForm.role,
          class: userForm.class,
          grade: userForm.grade,
          department: userForm.department,
          title: userForm.title
        }
        ElMessage.success('更新用户成功')
      }
    }

    userDialogVisible.value = false
  })
}

// 重置密码
const resetPassword = (user) => {
  if (!userManagementReady) {
    showReadOnlyNotice()
    return
  }
  resetPasswordForm.userId = user.id
  resetPasswordForm.password = ''
  resetPasswordForm.confirmPassword = ''
  resetPasswordDialogVisible.value = true
}

// 确认重置密码
const confirmResetPassword = () => {
  if (!userManagementReady) {
    showReadOnlyNotice()
    return
  }
  if (!resetPasswordForm.password) {
    ElMessage.warning('请输入新密码')
    return
  }

  if (resetPasswordForm.password !== resetPasswordForm.confirmPassword) {
    ElMessage.warning('两次输入的密码不一致')
    return
  }

  ElMessage.success('密码重置成功')
  resetPasswordDialogVisible.value = false
}

// 删除用户
const deleteUser = (user) => {
  if (!userManagementReady) {
    showReadOnlyNotice()
    return
  }
  ElMessageBox.confirm(
    `确定要删除用户 ${user.name} 吗？此操作不可恢复。`,
    '警告',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    }
  ).then(() => {
    const index = users.value.findIndex(u => u.id === user.id)
    if (index > -1) {
      users.value.splice(index, 1)
      ElMessage.success('删除用户成功')
    }
  }).catch(() => {
    // 取消删除
  })
}

onMounted(() => {
  loadClassList()
})
</script>

<style scoped>
.user-management {
  height: 100%;
}

.filter-card,
.table-card {
  margin-bottom: 20px;
}

.read-only-alert {
  margin-bottom: 20px;
}

.filter-form {
  display: flex;
  flex-wrap: wrap;
}

.user-info {
  display: flex;
  align-items: center;
}

.user-details {
  margin-left: 10px;
}

.user-name {
  font-weight: 500;
}

.user-extra {
  font-size: 12px;
  color: #999;
  margin-top: 3px;
}

.pagination-container {
  margin-top: 20px;
  display: flex;
  justify-content: center;
}

.my-page-header {
  padding: 20px;
}

</style>
