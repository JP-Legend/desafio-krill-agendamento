import { reactive } from 'vue'

/** Abas MDI (como no Atlas): cada tela aberta vira uma aba; Início é fixa. */
const CHAVE = 'krill.abas'
const inicial = () => {
  try {
    return JSON.parse(sessionStorage.getItem(CHAVE)) || []
  } catch {
    return []
  }
}
export const abas = reactive({ lista: inicial(), geracao: {} })
const salvar = () => sessionStorage.setItem(CHAVE, JSON.stringify(abas.lista))

export function rotuloDaRota(r) {
  const t = r.meta?.titulo || String(r.name || r.path)
  return r.params?.id ? `${t} ${r.params.id}` : t
}

export function abrirAba(r) {
  if (!r.meta?.titulo || r.meta.publica || r.path === '/inicio' || r.name === 'login') return
  const atual = abas.lista.find((a) => a.chave === r.path)
  if (atual) atual.caminho = r.fullPath
  else abas.lista.push({ chave: r.path, caminho: r.fullPath, rotulo: rotuloDaRota(r) })
  salvar()
}

/** Fecha a aba e devolve o caminho para onde ir se ela era a ativa. */
export function fecharAba(chave) {
  const i = abas.lista.findIndex((a) => a.chave === chave)
  if (i < 0) return '/inicio'
  abas.lista.splice(i, 1)
  abas.geracao[chave] = (abas.geracao[chave] || 0) + 1
  salvar()
  const vizinha = abas.lista[i] || abas.lista[i - 1]
  return vizinha ? vizinha.caminho : '/inicio'
}

export function moverAba(de, para) {
  if (de === para || de < 0 || para < 0) return
  const [a] = abas.lista.splice(de, 1)
  abas.lista.splice(para, 0, a)
  salvar()
}

export function limparAbas() {
  abas.lista.splice(0)
  salvar()
}

/** Chave do keep-alive: muda depois de fechar, para a tela reabrir do zero. */
export const chaveCache = (r) => `${r.path}#${abas.geracao[r.path] || 0}`
