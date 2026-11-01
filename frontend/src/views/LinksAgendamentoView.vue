<script setup>
import { computed, onMounted, ref } from 'vue'
import { api } from '../api'
import { fmtDataHora } from '../utils'
import { useEnvio } from '../composables/useEnvio'
import { useNotificar } from '../composables/useNotificar'

/** Porte de /recebimento/agendamentos/links do Gestão CD: um link fixo por CD, por loja ou para todos os CDs. */
const notificar = useNotificar()
const { enviando, enviar } = useEnvio()
const destinos = ref([])
const destino = ref(0)
const links = ref([])
const carregando = ref(false)
const confirmacao = ref(null)

const opcoes = computed(() => destinos.value.map((d, i) => ({ label: d.descricao, value: i })))

async function carregar() {
  carregando.value = true
  try {
    ;[destinos.value, links.value] = await Promise.all([api('/links-agendamento/destinos'), api('/links-agendamento')])
  } catch (e) {
    notificar.erro(e)
  } finally {
    carregando.value = false
  }
}

function pedirGerar() {
  confirmacao.value = { acao: 'gerar', texto: 'Gerar um novo link? O link ativo deste destino deixa de funcionar.', botao: 'Gerar link' }
}

function pedirRevogar(l) {
  confirmacao.value = { acao: 'revogar', link: l, texto: `Revogar o link de ${l.destino}? Fornecedores com ele não conseguirão mais agendar.`, botao: 'Revogar' }
}

function confirmar() {
  const c = confirmacao.value
  enviar(async () => {
    try {
      if (c.acao === 'gerar') {
        const d = destinos.value[destino.value]
        const novo = await api('/links-agendamento', { method: 'POST', body: { cdId: d.cdId, loja: d.loja } })
        await navigator.clipboard?.writeText(novo.url).catch(() => {})
        notificar.sucesso('Novo link público gerado e copiado. O anterior deste destino deixou de funcionar.')
      } else {
        await api(`/links-agendamento/${c.link.id}/revogacao`, { method: 'PATCH' })
        notificar.sucesso('Link revogado.')
      }
      confirmacao.value = null
      await carregar()
    } catch (e) {
      notificar.erro(e)
    }
  })
}

async function copiar(l) {
  try {
    await navigator.clipboard.writeText(l.url)
    notificar.sucesso('Link copiado')
  } catch {
    notificar.aviso('Selecione o link e copie manualmente')
  }
}

onMounted(carregar)
defineExpose({ destino })
</script>

<template>
  <div class="cabecalho"><h1>Links de agendamento</h1></div>
  <form class="painel filtros" @submit.prevent="pedirGerar">
    <div class="campo campo-destino"><label for="la-destino">CD ou loja</label><Select v-model="destino" input-id="la-destino" :options="opcoes" option-label="label" option-value="value" /></div>
    <Button type="submit" label="Gerar / regenerar link" icon="pi pi-link" class="btn-confirmar" :disabled="!destinos.length" />
  </form>
  <section class="painel">
    <div class="painel-titulo"><h2>Links</h2><span class="texto-suave">{{ links.length }} link(s)</span></div>
    <DataTable class="tabela-links" :value="links" :loading="carregando" size="small" striped-rows data-key="id">
      <template #empty><div class="vazio">Nenhum link gerado ainda.</div></template>
      <Column field="destino" header="Destino" />
      <Column header="Link">
        <template #body="{ data }">
          <div v-if="data.ativo" class="link-copia"><InputText :model-value="data.url" readonly :aria-label="`Link de ${data.destino}`" @focus="$event.target.select()" /><Button icon="pi pi-copy" class="btn-secundario" size="small" :aria-label="`Copiar link de ${data.destino}`" @click="copiar(data)" /></div>
          <span v-else class="texto-suave">desativado</span>
        </template>
      </Column>
      <Column header="Status">
        <template #body="{ data }"><span class="pilula" :class="data.ativo ? 'ativo' : 'inativo'">{{ data.ativo ? 'Ativo' : `Revogado ${fmtDataHora(data.revogadoEm)}` }}</span></template>
      </Column>
      <Column header="Criado"><template #body="{ data }">{{ fmtDataHora(data.criadoEm) }} · {{ data.criadoPor }}</template></Column>
      <Column header="Ação">
        <template #body="{ data }"><Button v-if="data.ativo" label="Revogar" class="btn-excluir" size="small" @click="pedirRevogar(data)" /></template>
      </Column>
    </DataTable>
  </section>

  <Dialog :visible="!!confirmacao" modal header="Links de agendamento" :style="{ width: '420px' }" @update:visible="confirmacao = null">
    <p>{{ confirmacao?.texto }}</p>
    <template #footer>
      <Button label="Voltar" class="btn-secundario" :disabled="enviando" @click="confirmacao = null" />
      <Button :label="confirmacao?.botao" class="btn-confirmar" :loading="enviando" :disabled="enviando" @click="confirmar" />
    </template>
  </Dialog>
</template>

<style scoped>
.campo-destino { min-width: 280px; }
.link-copia { display: flex; gap: 6px; align-items: center; min-width: 260px; }
.link-copia .p-inputtext { flex: 1; font-size: 12px; }
</style>
