<script setup>
import { computed, onMounted, ref } from 'vue'
import { api } from '../api'
import CadastroCrud from '../components/CadastroCrud.vue'

/** O limite do CD substitui o parâmetro geral só quando preenchido (mesma regra do backend em CentroDistribuicao). */
const padraoDia = ref(null)
const padraoFaixa = ref(null)
onMounted(async () => {
  const [p, faixas] = await Promise.all([api('/parametros/capacidade').catch(() => null), api('/faixas-horarias').catch(() => [])])
  padraoDia.value = p?.capacidadeDiariaPaletes ?? null
  const caps = [...new Set(faixas.filter((f) => f.ativo).map((f) => f.capacidadePaletes))]
  padraoFaixa.value = caps.length === 1 ? caps[0] : null
})
const efetivo = (proprio, padrao, rotuloPadrao) => (proprio != null ? `${proprio} pal.` : padrao != null ? `${padrao} pal. · ${rotuloPadrao}` : rotuloPadrao)
const campos = computed(() => [
  { campo: 'codigo', rotulo: 'Código', tipo: 'texto', obrigatorio: true, max: 10 },
  { campo: 'nome', rotulo: 'Nome', tipo: 'texto', obrigatorio: true, max: 80 },
  { campo: 'cidade', rotulo: 'Cidade', tipo: 'texto', max: 80 },
  {
    campo: 'capacidadeDiariaPaletes', rotulo: 'Limite por dia', tipo: 'numero', min: 1, maxNum: 100000,
    formatar: (d) => efetivo(d.capacidadeDiariaPaletes, padraoDia.value, 'padrão geral'),
    dica: `Em branco, vale o padrão geral${padraoDia.value != null ? ` de ${padraoDia.value} paletes` : ''} (Capacidade diária).`
  },
  {
    campo: 'capacidadeFaixaPaletes', rotulo: 'Limite por faixa', tipo: 'numero', min: 1, maxNum: 100000,
    formatar: (d) => efetivo(d.capacidadeFaixaPaletes, padraoFaixa.value, 'conforme cada faixa'),
    dica: `Em branco, vale a capacidade de cada faixa${padraoFaixa.value != null ? ` (${padraoFaixa.value} paletes)` : ''} (Faixas horárias).`
  },
  { campo: 'ativo', rotulo: 'Situação', tipo: 'ativo' }
])
const novo = () => ({ codigo: '', nome: '', cidade: '', capacidadeDiariaPaletes: null, capacidadeFaixaPaletes: null, ativo: true })
</script>

<template>
  <CadastroCrud titulo="Centros de distribuição" recurso="/cds" :campos="campos" :novo-padrao="novo" rotulo-novo="Novo CD" />
</template>
