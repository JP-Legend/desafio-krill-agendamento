<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { api } from '../api'
import { diaSemana, emailValido, erroCnpj, fmtData, mascaraCnpj } from '../utils'
import { useEnvio } from '../composables/useEnvio'
import { useNotificar } from '../composables/useNotificar'
import EscolhaHorario from '../components/EscolhaHorario.vue'

/** Formulário público do link de agendamento (porte do /agendar do Gestão CD): em branco, não lista nada. */
const route = useRoute()
const notificar = useNotificar()
const { enviando, enviar } = useEnvio()
const base = `/publico/agendar/${route.params.token}`
const info = ref(null)
const erro = ref('')
const dias = ref([])
/** Dias em que o destino do link não recebe, com o motivo (o backend também recusa). */
const fechados = ref({})
const escolha = ref(null)
const enviado = ref(null)
const form = reactive({ fornecedor: '', cnpj: '', emailFornecedor: '', idPedido: null, tipoCaminhaoId: null, tipoPaletizacaoId: null, quantidadePaletes: null, transportadora: '', observacao: '', siteWeb: '' })

const paletes = computed(() => form.quantidadePaletes || 0)
const tocado = reactive({ cnpj: false, email: false })
const msgCnpj = computed(() => (tocado.cnpj || form.cnpj.replace(/\D/g, '').length === 14 ? erroCnpj(form.cnpj) : ''))
const msgEmail = computed(() => (tocado.email && form.emailFornecedor.trim() && !emailValido(form.emailFornecedor) ? 'E-mail inválido' : ''))

async function carregar() {
  try {
    info.value = await api(base)
    await carregarDias()
  } catch (e) {
    erro.value = e.status === 404 ? 'Este link de agendamento foi desativado. Peça um novo link ao CD.' : e.message
  }
}

async function carregarDias() {
  escolha.value = null
  try {
    const [d, cal] = await Promise.all([api(`${base}/disponibilidade`), api(`${base}/calendario`).catch(() => [])])
    dias.value = d
    fechados.value = Object.fromEntries(cal.filter((x) => !x.aberto).map((x) => [x.data, x.motivo]))
  } catch (e) {
    notificar.erro(e)
  }
}

function validar() {
  const faltando = []
  if (!escolha.value) faltando.push('o dia e a faixa')
  if (!form.fornecedor.trim()) faltando.push('o fornecedor')
  if (!form.emailFornecedor.trim()) faltando.push('o e-mail')
  if (!form.idPedido) faltando.push('o nº do pedido de compra')
  if (!form.tipoCaminhaoId) faltando.push('o tipo de caminhão')
  if (!form.tipoPaletizacaoId) faltando.push('a paletização')
  if (!form.quantidadePaletes) faltando.push('a quantidade de paletes')
  return faltando
}

function agendarEntrega() {
  const faltando = validar()
  if (faltando.length) return notificar.aviso(`Informe ${faltando.join(', ')}`)
  tocado.cnpj = true
  tocado.email = true
  if (erroCnpj(form.cnpj)) return notificar.aviso(erroCnpj(form.cnpj))
  if (!emailValido(form.emailFornecedor)) return notificar.aviso('E-mail inválido')
  enviar(async () => {
    try {
      enviado.value = await api(base, {
        method: 'POST',
        body: { ...form, fornecedor: form.fornecedor.trim(), cnpj: form.cnpj.trim() || null, emailFornecedor: form.emailFornecedor.trim(), transportadora: form.transportadora.trim() || null, observacao: form.observacao.trim() || null, data: escolha.value.data, faixaHorariaId: escolha.value.faixaHorariaId }
      })
    } catch (e) {
      notificar.erro(e)
    }
  })
}

const textoCompartilhar = computed(() => enviado.value && `Agendamento de entrega (${form.fornecedor.trim()}). Consulte ou altere por este link: ${enviado.value.linkFornecedor}`)

async function copiar() {
  try {
    await navigator.clipboard.writeText(enviado.value.linkFornecedor)
    notificar.sucesso('Link copiado')
  } catch {
    notificar.aviso('Selecione o link e copie manualmente')
  }
}

onMounted(carregar)
defineExpose({ escolha, form, agendarEntrega })
/** "Sua entrega no CD 806 …" ou "Sua entrega na Loja 14 - KRILL BERTIOGA", conforme o destino do link. */
const ehLoja = computed(() => /^loja/i.test(info.value?.destino || ''))
const entregaEm = computed(() => (info.value?.destino ? `Sua entrega ${ehLoja.value ? 'na' : 'no'} ${info.value.destino}` : 'Agendamento de entrega'))
</script>

<template>
  <div class="publico">
    <header class="publico-topo">
      <img src="/img/rede-krill-logo.png" alt="Rede Krill" width="570" height="528" />
      <div><strong>Agendamento Rede Krill</strong><small>{{ entregaEm }}</small></div>
    </header>

    <div v-if="erro" class="painel vazio">{{ erro }}</div>

    <template v-else-if="enviado">
      <section class="painel">
        <h2>Agendamento nº {{ enviado.id }} enviado</h2>
        <p>{{ form.fornecedor }} · {{ enviado.destino }} · {{ diaSemana(enviado.data) }}, {{ fmtData(enviado.data) }} · {{ enviado.faixaHoraria }}. {{ ehLoja ? 'A loja foi avisada.' : 'O CD foi avisado.' }}</p>
        <div v-if="enviado.alertas.length" class="alerta aviso">
          <i class="pi pi-exclamation-triangle" />
          <div><div v-for="a in enviado.alertas" :key="a">{{ a }}</div><div>O agendamento foi registrado mesmo assim; o CD pode remarcar.</div></div>
        </div>
      </section>
      <section class="painel">
        <h2>Link do seu agendamento</h2>
        <p>Guarde este link: é por ele que você consulta, confirma presença e remarca a entrega. Não compartilhe com terceiros.</p>
        <div class="link-copia"><InputText :model-value="enviado.linkFornecedor" readonly aria-label="Link do seu agendamento" @focus="$event.target.select()" /><Button label="Copiar" icon="pi pi-copy" class="btn-confirmar" @click="copiar" /></div>
        <div class="linha-botoes">
          <a class="p-button btn-secundario" :href="`https://wa.me/?text=${encodeURIComponent(textoCompartilhar)}`" target="_blank" rel="noopener">Enviar pelo WhatsApp</a>
          <a class="p-button btn-secundario" :href="`mailto:${encodeURIComponent(form.emailFornecedor.trim())}?subject=${encodeURIComponent('Agendamento de entrega')}&body=${encodeURIComponent(textoCompartilhar)}`">Enviar por e-mail</a>
        </div>
      </section>
    </template>

    <form v-else-if="info" class="painel" @submit.prevent="agendarEntrega">
      <div class="painel-titulo"><h2>Agendar entrega</h2><span class="texto-suave">{{ info.destino }}</span></div>
      <div class="form-grade">
        <div class="campo c8"><label for="ag-forn">Fornecedor <span class="obrig">*</span></label><InputText id="ag-forn" v-model="form.fornecedor" maxlength="80" autocomplete="organization" /></div>
        <div class="campo c4"><label for="ag-cnpj">CNPJ</label><InputText id="ag-cnpj" :model-value="form.cnpj" maxlength="18" inputmode="numeric" :invalid="!!msgCnpj" @update:model-value="(v) => { form.cnpj = mascaraCnpj(v) }" @blur="tocado.cnpj = true" /><span v-if="msgCnpj" class="erro">{{ msgCnpj }}</span></div>
        <div class="campo c8"><label for="ag-email">E-mail <span class="obrig">*</span></label><InputText id="ag-email" v-model="form.emailFornecedor" type="email" maxlength="80" autocomplete="email" :invalid="!!msgEmail" @blur="tocado.email = true" /><span v-if="msgEmail" class="erro">{{ msgEmail }}</span></div>
        <div class="campo c4"><label for="ag-ped">Nº do pedido de compra <span class="obrig">*</span></label><InputNumber v-model="form.idPedido" input-id="ag-ped" :use-grouping="false" :min="1" /></div>
        <div class="campo c4"><label for="ag-cam">Tipo de caminhão <span class="obrig">*</span></label><Select v-model="form.tipoCaminhaoId" input-id="ag-cam" :options="info.tiposCaminhao" option-label="nome" option-value="id" /></div>
        <div class="campo c4"><label for="ag-pal">Paletização <span class="obrig">*</span></label><Select v-model="form.tipoPaletizacaoId" input-id="ag-pal" :options="info.tiposPaletizacao" option-label="nome" option-value="id" /></div>
        <div class="campo c4"><label for="ag-qtd">Paletes <span class="obrig">*</span></label><InputNumber v-model="form.quantidadePaletes" input-id="ag-qtd" :min="1" :max="10000" /></div>
        <div class="campo c12"><label for="ag-transp">Transportadora</label><InputText id="ag-transp" v-model="form.transportadora" maxlength="80" /></div>
      </div>
      <div class="campo-oculto" aria-hidden="true"><label for="ag-site">Site</label><input id="ag-site" v-model="form.siteWeb" tabindex="-1" autocomplete="off" /></div>

      <h3>Dia e faixa <span class="obrig">*</span></h3>
      <EscolhaHorario v-model="escolha" :dias="dias" :paletes="paletes" :fechados="fechados" />
      <p v-if="escolha" class="destaque-escolha">Entrega: <strong>{{ diaSemana(escolha.data) }}, {{ fmtData(escolha.data) }} · {{ escolha.descricao }}</strong></p>
      <div v-if="escolha && !escolha.cabe" class="alerta aviso">
        <i class="pi pi-exclamation-triangle" />
        <span>Esta faixa já está sem vaga para {{ paletes }} palete(s). O agendamento é aceito, mas o CD será avisado do excesso e pode remarcar.</span>
      </div>

      <div class="form-grade">
        <div class="campo c12"><label for="ag-obs">Observação</label><Textarea id="ag-obs" v-model="form.observacao" rows="2" maxlength="500" auto-resize /></div>
      </div>
      <Button type="submit" label="Enviar agendamento" icon="pi pi-send" class="btn-confirmar largo" :loading="enviando" :disabled="enviando" />
    </form>
  </div>
</template>

<style scoped>
.publico { max-width: 720px; margin: 0 auto; padding: 16px 12px 32px; }
.publico-topo { display: flex; gap: 10px; align-items: center; margin-bottom: 14px; }
.publico-topo img { height: 56px; width: auto; aspect-ratio: 570 / 528; object-fit: contain; }
.publico-topo div { display: flex; flex-direction: column; }
.publico-topo small { color: var(--cor-texto-suave); }
h3 { font-size: 14px; margin: 14px 0 8px; }
.campo-oculto { position: absolute; left: -10000px; width: 1px; height: 1px; overflow: hidden; }
.destaque-escolha { margin: 10px 0 0; }
.link-copia { display: flex; gap: 8px; align-items: center; }
.link-copia .p-inputtext { flex: 1; }
.linha-botoes { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 10px; }
.linha-botoes a { text-decoration: none; }
.largo { width: 100%; margin-top: 12px; }
</style>
