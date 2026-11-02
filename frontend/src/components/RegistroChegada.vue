<script setup>
import { reactive, watch } from 'vue'
import { api } from '../api'
import { pode } from '../sessao'
import { useEnvio } from '../composables/useEnvio'
import { useNotificar } from '../composables/useNotificar'
import CampoHora from './CampoHora.vue'

/**
 * Registro de chegada da portaria (mesmos campos da portaria do Gestão CD, sem notas fiscais):
 * empresa, transportadora, tipo de veículo, tipo de carga, placa, quantidade de paletes e horário de chegada,
 * mais o motorista. "Chegou" avisa todos do CD/loja (sino e push); "Não compareceu" encerra o agendamento.
 */
const props = defineProps({ cartao: { type: Object, default: null }, permiteNaoCompareceu: { type: Boolean, default: true } })
const visivel = defineModel('visivel', { type: Boolean, default: false })
const emit = defineEmits(['registrado'])
const notificar = useNotificar()
const { enviando, enviar } = useEnvio()
const VEICULOS = ['Carreta Sider', 'Carreta Baú', 'Carreta Lona', 'Truck Sider', 'Truck Baú', 'Truck Lona', 'Toco']
const agora = () => new Date().toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit', timeZone: 'America/Sao_Paulo' })
const f = reactive({ transportadora: '', tipoVeiculo: null, placa: '', paletes: null, hora: '', motorista: '' })
watch(visivel, (v) => {
  const k = props.cartao
  if (!v || !k) return
  Object.assign(f, { transportadora: k.transportadora || '', tipoVeiculo: VEICULOS.find((x) => x.toLowerCase().startsWith(String(k.tipoCaminhao || '').toLowerCase())) || null,
    placa: k.placa || '', paletes: k.paletes ?? k.quantidadePaletes ?? null, hora: agora(), motorista: k.motorista || '' })
})
const placaLimpa = () => f.placa.replace(/[^A-Za-z0-9]/g, '').toUpperCase().slice(0, 7)
function chegou() {
  const placa = placaLimpa()
  if (!f.tipoVeiculo) return notificar.aviso('Selecione o tipo de veículo')
  if (!/^[A-Z]{3}\d[A-Z0-9]\d{2}$/.test(placa)) return notificar.aviso('Informe a placa no formato ABC1D23 ou ABC1234')
  if (f.paletes == null) return notificar.aviso('Informe a quantidade de paletes')
  enviar(async () => {
    try {
      await api(`/agendamentos/${props.cartao.agendamentoId}/status`, { method: 'PATCH', body: {
        acao: 'CHEGADA', placa, motorista: f.motorista.trim() || null, horaChegada: f.hora || null,
        transportadora: f.transportadora.trim() || null, tipoVeiculo: f.tipoVeiculo, paletesChegada: f.paletes } })
      notificar.sucesso(`${placa} · ${props.cartao.fornecedor}. Todos foram avisados.`, 'Chegada registrada')
      window.dispatchEvent(new Event('krill:avisos'))
      visivel.value = false
      emit('registrado')
    } catch (e) { notificar.erro(e) }
  })
}
function naoCompareceu() {
  enviar(async () => {
    try {
      await api(`/agendamentos/${props.cartao.agendamentoId}/status`, { method: 'PATCH', body: { acao: 'NAO_COMPARECEU', motivo: 'Não compareceu (portaria)' } })
      notificar.sucesso(`${props.cartao.fornecedor} marcado como não compareceu`)
      visivel.value = false
      emit('registrado')
    } catch (e) { notificar.erro(e) }
  })
}
</script>

<template>
  <Dialog v-model:visible="visivel" modal header="Registrar chegada" :style="{ width: '640px' }" :breakpoints="{ '680px': '100vw' }" class="dialogo-chegada">
    <form v-if="cartao" class="form-grade registro-chegada" novalidate @submit.prevent="chegou">
      <div class="campo c12 resumo-chegada"><strong>{{ cartao.fornecedor }}</strong><span class="texto-suave">Pedido {{ cartao.idPedido }} · faixa {{ cartao.faixa || cartao.faixaHoraria }}</span></div>
      <div class="campo c6"><label for="rc-emp">Empresa</label><InputText id="rc-emp" :model-value="cartao.fornecedor" readonly /></div>
      <div class="campo c6"><label for="rc-tr">Transportadora</label><InputText id="rc-tr" v-model="f.transportadora" maxlength="80" placeholder="Ex.: RDR, Mantissa" autocomplete="organization" /></div>
      <div class="campo c6"><label for="rc-vei">Tipo de veículo <span class="obrig">*</span></label><Select v-model="f.tipoVeiculo" input-id="rc-vei" :options="VEICULOS" placeholder="Selecione" /></div>
      <div class="campo c6"><label for="rc-pl">Placa <span class="obrig">*</span></label><InputText id="rc-pl" v-model="f.placa" maxlength="8" autocapitalize="characters" autocomplete="off" placeholder="Digite a placa" @blur="f.placa = placaLimpa()" /></div>
      <div class="campo c6"><label for="rc-pal">Quantidade de paletes <span class="obrig">*</span></label><InputNumber v-model="f.paletes" input-id="rc-pal" :min="0" :max="10000" :use-grouping="false" /></div>
      <div class="campo c6"><label for="rc-h">Chegada (horário de Brasília)</label><CampoHora id="rc-h" v-model="f.hora" /></div>
      <div class="campo c12"><label for="rc-mot">Motorista</label><InputText id="rc-mot" v-model="f.motorista" maxlength="80" autocomplete="off" /></div>
      <button type="submit" hidden />
    </form>
    <template #footer>
      <div class="rodape-acoes">
        <div class="rodape-perigo"><Button v-if="pode(1204) && permiteNaoCompareceu" label="Não compareceu" icon="pi pi-ban" class="btn-excluir" :disabled="enviando" @click="naoCompareceu" /></div>
        <div class="rodape-principal"><Button label="Chegou" icon="pi pi-check" class="btn-confirmar" :loading="enviando" :disabled="enviando" @click="chegou" /></div>
      </div>
    </template>
  </Dialog>
</template>

<style scoped>
.resumo-chegada strong { display: block; font-size: 1rem; }
.registro-chegada :deep(input), .registro-chegada :deep(.p-select) { min-height: 44px; }
</style>
