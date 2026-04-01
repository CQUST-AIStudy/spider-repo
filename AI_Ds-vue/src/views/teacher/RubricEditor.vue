<template>
  <div class="rubric-editor">
    <el-page-header @back="$router.back()" title="返回" content="评分标准管理" />

    <el-card style="margin-top:16px">
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span>我的评分标准</span>
          <div style="display:flex;gap:8px;align-items:center">
            <el-button @click="pickTemplate" :loading="drafting">模板生成</el-button>
            <el-button type="primary" @click="showCreate">+ 新建标准</el-button>
          </div>
        </div>
      </template>
      <el-table :data="rubrics" v-loading="loading" stripe>
        <el-table-column prop="name" label="名称" />
        <el-table-column prop="subject" label="学科" width="150" />
        <el-table-column label="维度数" width="80">
          <template #default="{row}">{{ row.dimensions?.length || 0 }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180" />
        <el-table-column label="操作" width="120">
          <template #default="{row}">
            <el-button link type="primary" @click="editRubric(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- Create/Edit Dialog -->
    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑评分标准' : '新建评分标准'" width="700px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="学科"><el-input v-model="form.subject" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="自定义提示">
          <el-input v-model="form.customPrompt" type="textarea" :rows="3"
            placeholder="输入自定义评分提示词，AI 评分时会参考此内容（如：重点关注代码注释质量、要求实验数据真实等）" />
          <div style="font-size:12px;color:#9aa0a6;margin-top:4px">此提示词将作为 AI 评分的额外指导，影响所有维度的评分</div>
        </el-form-item>
      </el-form>

      <h4 style="margin:16px 0 8px">评分维度 <el-tag :type="weightSum === 100 ? 'success' : 'danger'" size="small">权重合计: {{ weightSum }}%</el-tag></h4>

      <div v-for="(dim, i) in form.dimensions" :key="i" style="display:flex;gap:8px;margin-bottom:8px;align-items:center">
        <el-input v-model="dim.name" placeholder="维度名称" style="width:150px" />
        <el-input v-model="dim.description" placeholder="描述" style="flex:1" />
        <el-input-number v-model="dim.maxScore" :min="1" :max="100" placeholder="满分" style="width:100px" />
        <el-input-number v-model="dim.weight" :min="1" :max="100" placeholder="权重%" style="width:100px" />
        <el-button type="danger" link @click="form.dimensions.splice(i, 1)">删除</el-button>
      </div>
      <el-button @click="addDimension" type="dashed" style="width:100%">+ 添加维度</el-button>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveRubric" :disabled="weightSum !== 100" :loading="saving">保存</el-button>
      </template>
    </el-dialog>
    <input ref="templateInput" type="file" accept=".pdf,.docx,.doc" style="display:none" @change="onTemplatePicked" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getRubrics, createRubric, updateRubric, getRubricDetail, draftRubricFromTemplate } from '@/api/tap'

const rubrics = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const saving = ref(false)
const drafting = ref(false)
const editingId = ref(null)
const templateInput = ref(null)

const form = ref({ name: '', subject: '', description: '', customPrompt: '', dimensions: [] })

const weightSum = computed(() => form.value.dimensions.reduce((s, d) => s + (d.weight || 0), 0))

async function loadRubrics() {
  loading.value = true
  try {
    const res = await getRubrics()
    rubrics.value = res?.data || []
  } catch (e) { ElMessage.error(e.message) }
  loading.value = false
}

function showCreate() {
  editingId.value = null
  form.value = { name: '', subject: '', description: '', customPrompt: '', dimensions: [
    { name: '代码正确性', description: '代码能否正确运行', maxScore: 20, weight: 40 },
    { name: '实验分析', description: '分析是否深入', maxScore: 15, weight: 30 },
    { name: '报告规范', description: '格式是否规范', maxScore: 15, weight: 30 },
  ]}
  dialogVisible.value = true
}

function pickTemplate() {
  templateInput.value?.click()
}

async function onTemplatePicked(event) {
  const file = event?.target?.files?.[0]
  if (!file) return
  drafting.value = true
  try {
    const res = await draftRubricFromTemplate(file)
    const draft = res?.data || res
    editingId.value = null
    form.value = {
      name: draft?.name || '',
      subject: draft?.subject || '',
      description: draft?.description || '',
      customPrompt: draft?.customPrompt || '',
      dimensions: (draft?.dimensions || []).map(d => ({
        name: d.name,
        description: d.description,
        maxScore: Number(d.maxScore || 10),
        weight: Number(d.weight || 0)
      }))
    }
    dialogVisible.value = true
    ElMessage.success('已根据模板生成评分标准草案')
  } catch (e) {
    ElMessage.error(e.message || '模板生成失败')
  } finally {
    drafting.value = false
    if (event?.target) event.target.value = ''
  }
}

async function editRubric(row) {
  try {
    const res = await getRubricDetail(row.id)
    const r = res?.data || row
    editingId.value = r.id
    form.value = { name: r.name, subject: r.subject, description: r.description,
      customPrompt: r.customPrompt || '',
      dimensions: (r.dimensions || []).map(d => ({ name: d.name, description: d.description, maxScore: d.maxScore, weight: d.weight }))
    }
    dialogVisible.value = true
  } catch (e) { ElMessage.error(e.message) }
}

function addDimension() {
  form.value.dimensions.push({ name: '', description: '', maxScore: 10, weight: 0 })
}

async function saveRubric() {
  saving.value = true
  try {
    if (editingId.value) {
      await updateRubric(editingId.value, form.value)
      ElMessage.success('更新成功')
    } else {
      await createRubric(form.value)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    loadRubrics()
  } catch (e) { ElMessage.error(e.message) }
  saving.value = false
}

onMounted(loadRubrics)
</script>

<style scoped>
.rubric-editor { padding: 0; }
.rubric-editor :deep(.el-card) {
  border-radius: 12px;
  border: 1px solid #dadce0;
  box-shadow: 0 1px 3px rgba(0,0,0,0.04);
}
</style>
