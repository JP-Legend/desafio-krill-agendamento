<script setup>
import CampoHora from './CampoHora.vue'
import { onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '../api'
import { erroCnpj, mascaraCnpj, soDigitos } from '../utils'
import { useEnvio } from '../composables/useEnvio'
import { useNotificar } from '../composables/useNotificar'

/**
 * Cadastro genérico (lista + diálogo de inclusão/edição).
 * campos: [{ campo, rotulo, tipo: 'texto'|'numero'|'hora'|'ativo', obrigatorio, max, min, coluna }]
 */
const props = defineProps({
  titulo: { type: String, required: true },
  recurso: { type: String, required: true },
  campos: { type: Array, required: true },
  novoPadrao: { type: Function, required: true },
  rotuloNovo: { type: String, default: 'Incluir' },
  paraForm: { type: Function, default: (r) => ({ ...r }) },
  paraBody: { type: Function, default: (f) => ({ ...f }) },
  validarExtra: { type: Function, default: () => null },
  params: { type: Object, default: null }
})

const emit = defineEmits(['salvo'])
const notificar = useNotificar()
const { enviando, enviar } = useEnvio()
const lista = ref([])
const carregando = ref(false)
const visivel = ref(false)
const form = reactive({})
const erros = ref({})
const route = useRoute()
const router = useRouter()
const caminhoDaAba = route.path

async function carregar() {
  carregando.value = true
  try {
    lista.value = await api(props.recurso, { params: props.params })
  } catch (e) {
    notificar.erro(e)
  } finally {
    carregando.value = false
  }
}

function abrir(registro) {
  Object.keys(form).forEach((k) => delete form[k])
  Object.assign(form, registro ? props.paraForm(registro) : props.novoPadrao())
  erros.value = {}
  visivel.value = true
}

function validar() {
  const e = {}
  props.campos.forEach((c) => {
    const v = form[c.campo]
    if (c.tipo === 'cnpj' && erroCnpj(v)) e[c.campo] = erroCnpj(v)
    else if (c.obrigatorio && (v === null || v === undefined || v === '' || (typeof v === 'string' && !v.trim()))) e[c.campo] = `Informe ${c.rotulo.toLowerCase()}`
    else if (c.max && typeof v === 'string' && v.length > c.max) e[c.campo] = `Máximo de ${c.max} caracteres`
  })
  const extra = props.validarExtra(form)
  if (extra) Object.assign(e, extra)
  erros.value = e
  return !Object.keys(e).length
}

function salvar() {
  if (!validar()) return
  enviar(async () => {
    try {
      const body = props.paraBody({ ...form })
      delete body.id
      await api(form.id ? `${props.recurso}/${form.id}` : props.recurso, { method: form.id ? 'PUT' : 'POST', body })
      notificar.sucesso(form.id ? 'Registro atualizado' : 'Registro incluído')
      visivel.value = false
      emit('salvo')
      carregar()
    } catch (e) {
      notificar.erro(e)
    }
  })
}

/** ?id=N (vindo da busca do topo) abre o registro direto. */
function abrirDaRota() {
  if (route.path !== caminhoDaAba) return
  const id = Number(route.query.id)
  if (!id) return
  const r = lista.value.find((x) => x.id === id)
  if (r) abrir(r)
}
onMounted(async () => {
  await carregar()
  abrirDaRota()
})
watch(() => route.query.id, () => abrirDaRota())
/** Fechou o diálogo aberto pela busca: tira o ?id da URL para a aba não reabrir ao voltar. */
watch(visivel, (v) => {
  if (v || route.path !== caminhoDaAba || !route.query.id) return
  const { id, ...resto } = route.query
  router.replace({ path: route.path, query: resto })
})
defineExpose({ carregar })
</script>

<template>
  <div class="cabecalho">
    <div class="titulo-tela"><h1>{{ titulo }}</h1><BotaoPermissoes /></div>
    <div class="acoes"><Button :label="rotuloNovo" icon="pi pi-plus" class="btn-confirmar" @click="abrir(null)" /></div>
  </div>
  <section class="painel">
    <DataTable :value="lista" :loading="carregando" size="small" striped-rows row-hover data-key="id" @row-click="(e) => abrir(e.data)">
      <template #empty><div class="vazio">Nenhum registro</div></template>
      <Column field="id" header="ID" class="num" />
      <Column v-for="c in campos.filter((x) => x.coluna !== false)" :key="c.campo" :header="c.rotulo" :class="c.tipo === 'numero' ? 'num' : ''">
        <template #body="{ data }">
          <Tag v-if="c.tipo === 'ativo'" :value="data[c.campo] ? 'Ativo' : 'Inativo'" :severity="data[c.campo] ? 'success' : 'secondary'" />
          <template v-else-if="c.formatar">{{ c.formatar(data) }}</template>
          <template v-else-if="c.tipo === 'hora'">{{ (data[c.campo] || '').slice(0, 5) }}</template>
          <template v-else>{{ data[c.campo] ?? '—' }}{{ c.sufixo && data[c.campo] != null ? c.sufixo : '' }}</template>
        </template>
      </Column>
      <Column header="" style="width: 60px"><template #body="{ data }"><Button icon="pi pi-pencil" text rounded aria-label="Editar" @click.stop="abrir(data)" /></template></Column>
    </DataTable>
    <slot name="rodape" :lista="lista" />
  </section>

  <Dialog v-model:visible="visivel" modal :header="form.id ? `Editar · ID ${form.id}` : rotuloNovo" :style="{ width: '520px' }" :breakpoints="{ '560px': '96vw' }">
    <form class="grade" novalidate @submit.prevent="salvar">
      <div v-for="c in campos" :key="c.campo" class="campo" :class="c.tipo === 'texto' || c.tipo === 'cnpj' ? 'c12' : 'c6'">
        <label :for="c.campo">{{ c.rotulo }} <span v-if="c.obrigatorio" class="obrig">*</span></label>
        <InputText v-if="c.tipo === 'cnpj'" :id="c.campo" :model-value="form[c.campo]" maxlength="18" inputmode="numeric" :invalid="!!erros[c.campo]" @update:model-value="(v) => { form[c.campo] = mascaraCnpj(v); if (erros[c.campo] || soDigitos(v).length === 14) erros[c.campo] = erroCnpj(form[c.campo]) }" />
        <InputText v-else-if="c.tipo === 'texto'" :id="c.campo" v-model="form[c.campo]" :type="c.email ? 'email' : 'text'" :maxlength="c.max" :invalid="!!erros[c.campo]" />
        <InputNumber v-else-if="c.tipo === 'numero'" v-model="form[c.campo]" :input-id="c.campo" :min="c.min ?? 0" :max="c.maxNum" :max-fraction-digits="c.decimais ?? 0" :invalid="!!erros[c.campo]" />
        <CampoHora v-else-if="c.tipo === 'hora'" :id="c.campo" v-model="form[c.campo]" :invalid="!!erros[c.campo]" />
        <Select v-else-if="c.tipo === 'select'" v-model="form[c.campo]" :input-id="c.campo" :options="c.opcoes" option-label="label" option-value="value" :invalid="!!erros[c.campo]" show-clear />
        <ToggleSwitch v-else-if="c.tipo === 'ativo'" v-model="form[c.campo]" :input-id="c.campo" />
        <span v-if="c.dica" class="dica">{{ c.dica }}</span>
        <span v-if="erros[c.campo]" class="erro">{{ erros[c.campo] }}</span>
      </div>
      <button type="submit" hidden />
    </form>
    <template #footer>
      <Button label="Cancelar" class="btn-secundario" :disabled="enviando" @click="visivel = false" />
      <Button label="Salvar" icon="pi pi-check" class="btn-confirmar" :loading="enviando" :disabled="enviando" @click="salvar" />
    </template>
  </Dialog>
</template>
