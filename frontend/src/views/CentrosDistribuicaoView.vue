<script setup>
import CadastroCrud from '../components/CadastroCrud.vue'

const campos = [
  { campo: 'codigo', rotulo: 'Código', tipo: 'texto', obrigatorio: true, max: 10 },
  { campo: 'nome', rotulo: 'Nome', tipo: 'texto', obrigatorio: true, max: 80 },
  { campo: 'cidade', rotulo: 'Cidade', tipo: 'texto', max: 80 },
  { campo: 'capacidadeDiariaPaletes', rotulo: 'Capacidade/dia', tipo: 'numero', min: 1, maxNum: 100000, sufixo: ' pal.' },
  { campo: 'capacidadeFaixaPaletes', rotulo: 'Capacidade/faixa', tipo: 'numero', min: 1, maxNum: 100000, sufixo: ' pal.' },
  { campo: 'ativo', rotulo: 'Situação', tipo: 'ativo' }
]
const novo = () => ({ codigo: '', nome: '', cidade: '', capacidadeDiariaPaletes: null, capacidadeFaixaPaletes: null, ativo: true })
</script>

<template>
  <CadastroCrud titulo="Centros de distribuição" recurso="/cds" :campos="campos" :novo-padrao="novo" rotulo-novo="Novo CD">
  </CadastroCrud>
</template>
