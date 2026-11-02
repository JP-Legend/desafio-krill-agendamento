<script setup>
import { ref, watch } from 'vue'

/** Hora sempre em 24h (HH:MM), independente do idioma do navegador (o input type=time mostra AM/PM em inglês). */
const modelo = defineModel({ type: String, default: '' })
defineOptions({ inheritAttrs: false })
const texto = ref('')
watch(modelo, (v) => { const t = (v || '').slice(0, 5); if (t !== texto.value) texto.value = t }, { immediate: true })
function digitar(e) {
  let d = String(e.target.value || '').replace(/\D/g, '').slice(0, 4)
  if (d.length >= 3) d = `${d.slice(0, 2)}:${d.slice(2)}`
  texto.value = d
  e.target.value = d
  if (/^([01]\d|2[0-3]):[0-5]\d$/.test(d)) modelo.value = d
  else if (!d) modelo.value = ''
}
function sair() {
  const d = texto.value.replace(/\D/g, '')
  if (!d) return
  const h = Math.min(23, Number(d.length <= 2 ? d : d.slice(0, d.length - 2)))
  const mi = Math.min(59, Number(d.length <= 2 ? 0 : d.slice(-2)))
  const v = `${String(h).padStart(2, '0')}:${String(mi).padStart(2, '0')}`
  texto.value = v
  modelo.value = v
}
</script>

<template>
  <InputText v-bind="$attrs" :model-value="texto" inputmode="numeric" maxlength="5" placeholder="00:00" autocomplete="off" @input="digitar" @blur="sair" />
</template>
