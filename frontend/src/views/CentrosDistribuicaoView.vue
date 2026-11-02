<script setup>
import CadastroCrud from '../components/CadastroCrud.vue'

/** Limites e dias de recebimento do CD ficam num único lugar: Cadastros › Capacidade e calendário. */
const campos = [
  { campo: 'codigo', rotulo: 'Código', tipo: 'texto', obrigatorio: true, max: 10 },
  { campo: 'nome', rotulo: 'Nome', tipo: 'texto', obrigatorio: true, max: 80 },
  { campo: 'cidade', rotulo: 'Cidade', tipo: 'texto', max: 80 },
  { campo: 'ativo', rotulo: 'Situação', tipo: 'ativo' }
]
const novo = () => ({ codigo: '', nome: '', cidade: '', ativo: true })
</script>

<template>
  <CadastroCrud titulo="Centros de distribuição" recurso="/cds" :campos="campos" :novo-padrao="novo" rotulo-novo="Novo CD" />
  <p class="texto-suave nota-limites">
    Limites por dia e por faixa e os dias de recebimento de cada CD ficam em
    <RouterLink to="/cadastros/capacidade-recebimento">Capacidade e calendário</RouterLink>.
  </p>
</template>

<style scoped>
.nota-limites { margin: 10px 2px 0; font-size: 13px; }
</style>
