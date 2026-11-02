<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { api } from '../api'
import { fmtData, fmtDataHora } from '../utils'
import { useTiposPalete } from '../composables/useTiposPalete'
import { code128Svg, qrSvg } from '../codigosBarras'

/**
 * Impressão do vale-palete igual à do Gestão CD (valePaletePrint): folha A4, código de barras Code128 + QR,
 * faixa de dados, seções numeradas, tabela de saldo, condições e as duas assinaturas.
 * Sem nome do sistema e sem URL: @page com margem 0 tira cabeçalho/rodapé do navegador e o título fica vazio.
 * O código lido (EMPRESA-VP-...) é derivado do código do vale, então serve também para vales antigos.
 */
const route = useRoute()
const tiposPalete = useTiposPalete()
const vale = ref(null)
const cd = ref(null)
const erro = ref('')

const tipos = computed(() => (vale.value ? Object.keys(vale.value.entregues) : []))
const soma = (m) => Object.values(m || {}).reduce((a, b) => a + (b || 0), 0)
const saldo = computed(() => soma(vale.value?.saldo))
const STATUS = { PENDENTE: 'Pendente de assinatura', ASSINADO: 'Assinado', QUITADO: 'Quitado', CANCELADO: 'Cancelado' }
const statusTexto = computed(() => (vale.value.status === 'CANCELADO' ? 'Cancelado' : saldo.value > 0 ? 'Saldo a devolver' : 'Quitado'))
const aberto = computed(() => ['PENDENTE', 'ASSINADO'].includes(vale.value.status))
const leitura = computed(() => vale.value?.codigoLeitura || vale.value?.codigo || '')
const barras = computed(() => code128Svg(leitura.value, { height: 54, module: 1.6 }))
const qr = computed(() => qrSvg(leitura.value, 96))
const emitido = computed(() => fmtDataHora(vale.value.criadoEm).replace(',', ''))
const local = computed(() => (cd.value ? `CD ${cd.value.codigo}` : 'Centro de distribuição'))
const fmtN = (n) => (n || 0).toLocaleString('pt-BR')

let estilo = null
const tituloAnterior = document.title
onMounted(async () => {
  estilo = document.createElement('style')
  estilo.textContent = '@page { size: A4 portrait; margin: 0; } html, body { margin: 0; background: #fff; }'
  document.head.appendChild(estilo)
  document.title = ''
  try {
    await tiposPalete.carregar().catch(() => {})
    vale.value = await api(`/vales-palete/${route.params.id}`)
    if (vale.value.agendamentoId) {
      const a = await api(`/agendamentos/${vale.value.agendamentoId}`).catch(() => null)
      if (a?.cdId) cd.value = (await api('/cds').catch(() => [])).find((c) => c.id === a.cdId) || null
    }
    await nextTick()
    if (route.query.auto === '1') setTimeout(() => window.print(), 250)
  } catch (e) {
    erro.value = e.status === 404 ? 'Vale-palete não encontrado' : e.message
  }
})
onBeforeUnmount(() => {
  estilo?.remove()
  document.title = tituloAnterior
})
const imprimir = () => window.print()
</script>

<template>
  <main v-if="erro" class="krdoc krdoc-vale"><section class="krdoc-sheet"><h1>{{ erro }}</h1></section></main>
  <main v-else-if="vale" class="vale-print krdoc krdoc-vale" data-vale-palete-print>
    <div class="no-print krdoc-actions">
      <Button label="Imprimir" icon="pi pi-print" class="btn-confirmar" @click="imprimir" />
      <RouterLink :to="aberto ? `/vales-palete/${vale.id}?devolver=1` : `/vales-palete/${vale.id}`" class="p-button btn-secundario krdoc-voltar">{{ aberto ? 'Registrar devolução' : 'Voltar' }}</RouterLink>
    </div>
    <section class="krdoc-sheet">
      <header class="krdoc-header">
        <div class="krdoc-brand">
          <img class="krdoc-logo" src="/img/rede-krill-logo.png" alt="Rede Krill" width="570" height="528" />
          <div><h1>Rede Krill</h1><p>{{ local }} · Recebimento</p></div>
        </div>
        <div class="krdoc-docid">
          <span class="krdoc-kicker">Comprovante de saldo de paletes</span>
          <h2>VALE-PALETE</h2>
          <strong class="mono">{{ vale.codigo }}</strong>
          <span class="krdoc-badge" :class="saldo > 0 && aberto ? 'is-warn' : 'is-ok'">{{ statusTexto }}</span>
        </div>
      </header>
      <div class="pal-codes">
        <div class="pal-bar"><span class="pal-svg" v-html="barras" /><span class="mono">{{ leitura }}</span></div>
        <p class="muted">Via do CD e da empresa. Apresente este código no CD: a leitura abre o vale para a devolução.</p>
        <span class="pal-svg" v-html="qr" />
      </div>
      <div class="krdoc-strip">
        <div class="krdoc-field"><label>Emissão</label><strong><span class="mono">{{ emitido }}</span></strong></div>
        <div class="krdoc-field"><label>Local</label><strong>{{ local }}</strong></div>
        <div class="krdoc-field"><label>Situação</label><strong>{{ STATUS[vale.status] }}</strong></div>
        <div class="krdoc-field"><label>Emitido por</label><strong>{{ vale.criadoPor || '-' }}</strong></div>
      </div>
      <section class="krdoc-section">
        <h3><span>1</span>Identificação da descarga</h3>
        <div class="krdoc-grid cols-3">
          <div class="krdoc-field"><label>Fornecedor / transportadora</label><strong>{{ vale.fornecedor }}</strong></div>
          <div class="krdoc-field"><label>Agendamento</label><strong><span class="mono">{{ vale.agendamentoId ? `#${vale.agendamentoId}` : '-' }}</span></strong></div>
          <div class="krdoc-field"><label>Data da descarga</label><strong><span class="mono">{{ fmtData(vale.data) }}</span></strong></div>
          <div class="krdoc-field"><label>Motorista</label><strong>{{ vale.motorista || '-' }}</strong></div>
          <div class="krdoc-field"><label>Placa</label><strong><span class="mono">{{ vale.placa || '-' }}</span></strong></div>
          <div class="krdoc-field"><label>Controle</label><strong><span class="mono">{{ vale.codigo }}</span></strong></div>
        </div>
      </section>
      <section class="krdoc-section">
        <h3><span>2</span>Saldo de paletes a devolver</h3>
        <table class="krdoc-table">
          <thead><tr><th>Tipo</th><th class="num">Recebidos c/ carga</th><th class="num">Devolvidos</th><th class="num">Saldo a devolver</th></tr></thead>
          <tbody>
            <tr v-for="t in tipos" :key="t"><td>{{ tiposPalete.rotulo(t) }}</td><td class="num mono">{{ fmtN(vale.entregues[t]) }}</td><td class="num mono">{{ fmtN(vale.devolvidos[t]) }}</td><td class="num mono">{{ fmtN(vale.saldo[t]) }}</td></tr>
          </tbody>
          <tfoot><tr><td>TOTAL</td><td class="num mono">{{ fmtN(vale.totalEntregues) }}</td><td class="num mono">{{ fmtN(vale.totalDevolvidos) }}</td><td class="num mono">{{ fmtN(vale.totalSaldo) }}</td></tr></tfoot>
        </table>
      </section>
      <p class="krdoc-term"><strong>Observações.</strong> {{ vale.observacao || '-' }}</p>
      <p class="krdoc-term"><strong>Condições.</strong> Este vale comprova o saldo de paletes retornáveis em favor do fornecedor/transportadora. A devolução ocorre mediante apresentação deste documento (ou do código {{ vale.codigo }}) no {{ local }}.</p>
      <div class="krdoc-signs cols-2 krdoc-signs-vale">
        <div v-for="[papel, titulo, ass, nomePadrao] in [['motorista', 'Motorista / transportadora', vale.assinaturaMotorista, vale.motorista], ['conferente', `Conferente · ${local}`, vale.assinaturaConferente, '']]" :key="papel"
             class="krdoc-signbox" :class="{ 'is-pending': !ass }">
          <span class="krdoc-signbox-label">Assinatura do {{ papel }}</span>
          <img v-if="ass" class="krdoc-sign-img" :src="ass.imagem" :alt="`Assinatura do ${papel}`" />
          <div v-else class="krdoc-sign-space"><em>Assinatura pendente</em></div>
          <strong>{{ titulo }}</strong>
          <small>{{ ass?.nome || nomePadrao || 'Nome' }}{{ ass ? ` · ${fmtDataHora(ass.em).replace(',', '')} BRT` : '' }}</small>
        </div>
      </div>
      <footer class="krdoc-footer"><span>Vale-palete digital · Emitido em {{ emitido }} BRT</span><span class="mono">{{ vale.codigo }}</span></footer>
    </section>
  </main>
</template>

<style scoped>
/* Cópia do CSS .krdoc / .pal-codes do Gestão CD (atlas-theme.css + PALETES_CODES_CSS), com as cores em variáveis. */
.krdoc { --kr-navy: #0b2545; --kr-line: #cfd8e3; --kr-ink: #0f172a; --kr-muted: #64748b; --kr-fundo: #eef2f7; --kr-folha: #fff; --kr-borda-folha: #d5dde8;
  --kr-ok: #16a34a; --kr-ok-texto: #15803d; --kr-alerta: #ea580c; --kr-alerta-texto: #c2410c; --kr-zebra: #f8fafc; --kr-total: #e8eef7; --kr-linha-clara: #e2e8f0;
  --kr-termo: #f1f5f9; --kr-termo-texto: #334155; --kr-rodape: #94a3b8; --kr-pendente: #b45309; --kr-pendente-fundo: #fef3c7;
  color: var(--kr-ink); font-family: Inter, Arial, sans-serif; -webkit-print-color-adjust: exact; print-color-adjust: exact; }
.krdoc .mono { font-family: "JetBrains Mono", "DejaVu Sans Mono", Consolas, monospace; letter-spacing: .01em; }
.krdoc .num { text-align: right; }
.krdoc.krdoc-vale { background: var(--kr-fundo); padding: 18px 0; min-height: 100vh; }
.krdoc-actions { display: flex; flex-wrap: wrap; gap: 8px; width: 190mm; max-width: calc(100% - 16px); margin: 0 auto 12px; }
.krdoc-voltar { text-decoration: none; }
.krdoc-sheet { box-sizing: border-box; width: min(210mm, 100%); margin: 0 auto; background: var(--kr-folha); border: 1px solid var(--kr-borda-folha); padding: 11mm 12mm 9mm; display: flex; flex-direction: column; gap: 9px; }
.krdoc-header { display: grid; grid-template-columns: 1fr auto; gap: 16px; align-items: start; padding-bottom: 10px; border-bottom: 3px solid var(--kr-navy); }
.krdoc-brand { display: flex; gap: 12px; align-items: center; }
.krdoc-logo { width: 64px; height: auto; max-height: 58px; object-fit: contain; }
.krdoc-brand h1 { margin: 0; font-size: 15px; font-weight: 800; color: var(--kr-ink); letter-spacing: .01em; }
.krdoc-brand p { margin: 2px 0 0; font-size: 10.5px; color: var(--kr-muted); }
.krdoc-docid { display: flex; flex-direction: column; align-items: flex-end; text-align: right; gap: 2px; }
.krdoc-kicker { font-size: 8.5px; font-weight: 700; letter-spacing: .16em; text-transform: uppercase; color: var(--kr-muted); }
.krdoc-docid h2 { margin: 0; font-size: 20px; font-weight: 800; color: var(--kr-navy); letter-spacing: .02em; }
.krdoc-docid > strong { font-size: 20px; color: var(--kr-ink); }
.krdoc-badge { margin-top: 4px; padding: 3px 10px; border: 1.5px solid var(--kr-ok); color: var(--kr-ok-texto); font-size: 9px; font-weight: 800; letter-spacing: .12em; text-transform: uppercase; }
.krdoc-badge.is-warn { border-color: var(--kr-alerta); color: var(--kr-alerta-texto); }
.pal-codes { display: grid; grid-template-columns: auto 1fr auto; align-items: center; gap: 14px; margin: 8px 0 10px; padding: 8px 0; border-top: 1px solid var(--kr-linha-clara); border-bottom: 1px solid var(--kr-linha-clara); }
.pal-bar { display: flex; flex-direction: column; align-items: center; gap: 2px; }
.pal-bar :deep(svg) { max-width: 100%; height: 54px; }
.pal-bar > .mono { font-size: 11px; letter-spacing: .08em; }
.pal-codes p { font-size: 11px; margin: 0; color: var(--kr-muted); }
.pal-svg { display: inline-flex; }
.krdoc-strip { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); border: 1px solid var(--kr-line); border-top: 3px solid var(--kr-navy); }
.krdoc-strip .krdoc-field { padding: 7px 10px; border-right: 1px solid var(--kr-line); }
.krdoc-strip .krdoc-field:last-child { border-right: 0; }
.krdoc-field label { display: block; margin-bottom: 3px; font-size: 8px; font-weight: 700; letter-spacing: .12em; text-transform: uppercase; color: var(--kr-muted); }
.krdoc-field strong { display: block; font-size: 11.5px; font-weight: 600; color: var(--kr-ink); overflow-wrap: anywhere; }
.krdoc-section { break-inside: avoid; page-break-inside: avoid; }
.krdoc-section h3 { display: flex; align-items: center; gap: 8px; margin: 0 0 5px; font-size: 9.5px; font-weight: 800; letter-spacing: .16em; text-transform: uppercase; color: var(--kr-navy); }
.krdoc-section h3::after { content: ""; flex: 1; border-top: 1px solid var(--kr-line); }
.krdoc-section h3 span { display: inline-grid; place-items: center; width: 15px; height: 15px; background: var(--kr-navy); color: var(--kr-folha); font-size: 9px; letter-spacing: 0; }
.krdoc-grid { display: grid; border: 1px solid var(--kr-line); border-left: 3px solid var(--kr-navy); }
.krdoc-grid.cols-3 { grid-template-columns: repeat(3, minmax(0, 1fr)); }
.krdoc-grid .krdoc-field { padding: 6px 10px; border-right: 1px solid var(--kr-line); border-bottom: 1px solid var(--kr-line); }
.krdoc-table { width: 100%; table-layout: fixed; border-collapse: collapse; font-size: 10px; }
.krdoc-table thead th { background: var(--kr-navy); color: var(--kr-folha); padding: 5px 7px; font-size: 8px; font-weight: 700; letter-spacing: .1em; text-transform: uppercase; text-align: left; }
.krdoc-table thead th.num { text-align: right; }
.krdoc-table td { padding: 4px 7px; overflow-wrap: anywhere; border-bottom: 1px solid var(--kr-linha-clara); vertical-align: middle; }
.krdoc-table tbody tr:nth-child(even) td { background: var(--kr-zebra); }
.krdoc-table tfoot td { background: var(--kr-total); font-weight: 800; padding: 5px 7px; }
.krdoc-term { margin: 2px 0 0; padding: 8px 10px; background: var(--kr-termo); border: 1px solid var(--kr-linha-clara); font-size: 9px; line-height: 1.45; color: var(--kr-termo-texto); }
.krdoc-signs { display: grid; margin-top: 22px; break-inside: avoid; page-break-inside: avoid; }
.krdoc-signs.cols-2 { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 40px; }
.krdoc-signs > div { display: flex; flex-direction: column; justify-content: flex-end; }
.krdoc-signs strong { display: block; border-top: 1px solid var(--kr-ink); padding-top: 4px; font-size: 10px; }
.krdoc-signs small { font-size: 8.5px; color: var(--kr-muted); }
.krdoc-sign-img { display: block; height: 64px; width: 100%; object-fit: contain; object-position: bottom; }
.krdoc-signs.krdoc-signs-vale { gap: 16px; }
.krdoc-signs-vale .krdoc-signbox { border: 1px solid var(--kr-line); border-radius: 6px; padding: 6px 10px 8px; justify-content: flex-start; }
.krdoc-signbox-label { font-size: 8px; font-weight: 700; letter-spacing: .06em; text-transform: uppercase; color: var(--kr-muted); }
.krdoc-signs-vale .krdoc-sign-img { height: 70px; }
.krdoc-signs-vale .krdoc-sign-space { height: 70px; display: flex; align-items: center; justify-content: center; }
.krdoc-signs-vale .krdoc-sign-space em { font-size: 9px; font-style: normal; font-weight: 700; color: var(--kr-pendente); background: var(--kr-pendente-fundo); border-radius: 999px; padding: 2px 8px; }
.krdoc-signs-vale .krdoc-signbox.is-pending { border-style: dashed; }
.krdoc-footer { display: flex; justify-content: space-between; gap: 12px; margin-top: auto; padding-top: 6px; border-top: 1px solid var(--kr-line); font-size: 8px; color: var(--kr-rodape); }
@media print {
  .no-print { display: none; }
  .krdoc.krdoc-vale { background: var(--kr-folha); padding: 0; min-height: 0; }
  .krdoc-sheet { width: 210mm; max-width: 210mm; border: 0; padding: 10mm 11mm 8mm; min-height: 0; }
  .krdoc-signs-vale .krdoc-sign-space em { background: none; border: 1px dashed var(--kr-pendente); }
}
@media screen and (max-width: 767px) {
  .krdoc.krdoc-vale { padding: 8px; box-sizing: border-box; max-width: 100vw; overflow-x: hidden; }
  .krdoc-actions { width: auto; display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); margin: 0 0 10px; }
  .krdoc-sheet { width: 100%; max-width: 100%; padding: 14px 12px; }
  .krdoc-header { grid-template-columns: minmax(0, 1fr); gap: 10px; }
  .krdoc-docid { align-items: flex-start; text-align: left; }
  .pal-codes { grid-template-columns: minmax(0, 1fr) auto; }
  .pal-codes p { grid-column: 1 / -1; grid-row: 2; }
  .krdoc-strip, .krdoc-grid.cols-3 { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .krdoc-signs.cols-2.krdoc-signs-vale { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }
  .krdoc-footer { flex-direction: column; gap: 4px; }
}
</style>
