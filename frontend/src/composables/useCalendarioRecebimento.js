import { computed, ref } from 'vue'
import { deIso } from '../utils'

/**
 * Dias em que o destino (CD ou loja) recebe mercadoria. `carregar` recebe uma função que devolve
 * [{ data, aberto, motivo }]; dias fechados viram datas desabilitadas no DatePicker, com o motivo.
 */
export function useCalendarioRecebimento() {
  const dias = ref([])
  const porData = computed(() => Object.fromEntries(dias.value.map((d) => [d.data, d])))
  const datasFechadas = computed(() => dias.value.filter((d) => !d.aberto).map((d) => deIso(d.data)))
  const fechado = (iso) => {
    const d = porData.value[iso]
    return d && !d.aberto ? d : null
  }
  let seq = 0
  async function carregar(buscar) {
    const atual = ++seq
    try {
      const r = await buscar()
      if (atual === seq) dias.value = r || []
    } catch {
      if (atual === seq) dias.value = []
    }
  }
  return { dias, porData, datasFechadas, fechado, carregar }
}
