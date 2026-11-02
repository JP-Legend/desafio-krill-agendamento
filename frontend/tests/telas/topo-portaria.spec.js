import { afterEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, logar, montar, sair, simularApi, PERMISSOES } from '../montar'
import InicioView from '../../src/views/InicioView.vue'
import BuscaTopo from '../../src/components/BuscaTopo.vue'
import SinoAvisos from '../../src/components/SinoAvisos.vue'
import PortariaView from '../../src/views/PortariaView.vue'
import ValePaleteView from '../../src/views/ValePaleteView.vue'
import CentrosDistribuicaoView from '../../src/views/CentrosDistribuicaoView.vue'
import { menuItens } from '../../src/menu'
import { hoje } from '../../src/utils'

afterEach(() => {
  sair()
  vi.useRealTimers()
})

const botao = (w, texto) => w.findAll('button').find((b) => b.text().includes(texto))
const aviso = (id, titulo, mensagem, lido = false) => ({ aviso: { id, titulo, mensagem, url: '/portaria', criadoEm: '2026-11-02T12:00:00Z' }, lido })

describe('grade da home sem buracos', () => {
  it('usuário comum: os três grupos se dividem em 2 + 1', async () => {
    logar('USUARIO')
    simularApi({ 'GET /dashboard': { __erro: true, status: 500, corpo: {} } })
    const { w } = await montar(InicioView)
    expect(w.findAll('.centrais-coluna').map((c) => c.findAll('.central-card').length)).toEqual([2, 1])
  })

  it('portaria fica no grupo Recebimento e cada tela aparece uma vez', () => {
    const m = menuItens(() => true)
    expect(m.find((g) => g.grupo === 'Recebimento').itens.map((i) => i.to)).toEqual(['/portaria', '/patio', '/divergencias', '/relatorios/descarga'])
    const rotas = m.flatMap((g) => g.itens.map((i) => i.to))
    expect(rotas.filter((r) => r === '/portaria')).toHaveLength(1)
    expect(menuItens((c) => PERMISSOES.USUARIO.includes(c)).flatMap((g) => g.itens.map((i) => i.to))).toContain('/portaria')
  })
})

describe('busca do topo (OmniSearch)', () => {
  it('Ctrl+K abre, acha telas e registros e Enter abre o primeiro', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    logar('USUARIO')
    const api = simularApi({ 'GET /busca': [{ grupo: 'Agendamentos', titulo: 'Distribuidora Beta', detalhe: '03/11/2026 08:00 · Pedido 98765', url: '/agendamentos/7' }] })
    const { w, router } = await montar(BuscaTopo)
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'k', ctrlKey: true }))
    await flushPromises()
    expect(w.find('.busca-topo').classes()).toContain('busca-topo--aberta')
    const campo = w.find('#busca-topo-campo')
    await campo.setValue('portaria')
    await vi.advanceTimersByTimeAsync(350)
    await flushPromises()
    expect(w.text()).toContain('Portaria')
    expect(w.text()).not.toContain('Usuários')
    await campo.setValue('beta')
    await vi.advanceTimersByTimeAsync(350)
    await flushPromises()
    expect(api.ultima('GET /busca').params.q).toBe('beta')
    expect(w.text()).toContain('Distribuidora Beta')
    await campo.trigger('keydown', { key: 'Enter' })
    await flushPromises()
    expect(router.currentRoute.value.fullPath).toBe('/agendamentos/7')
  })

  it('usuário comum não acha telas de cadastro; Esc limpa e depois recolhe', async () => {
    logar('USUARIO')
    simularApi({ 'GET /busca': [] })
    const { w } = await montar(BuscaTopo)
    await w.find('.busca-topo').trigger('click')
    const campo = w.find('#busca-topo-campo')
    await campo.setValue('fornecedores')
    await new Promise((r) => setTimeout(r, 350))
    await flushPromises()
    expect(w.text()).toContain('Nada encontrado')
    await campo.trigger('keydown', { key: 'Escape' })
    expect(campo.element.value).toBe('')
    await campo.trigger('keydown', { key: 'Escape' })
    expect(w.find('.busca-topo').classes()).not.toContain('busca-topo--aberta')
  })
})

describe('sino de notificações', () => {
  it('mostra contador, lista e marca como lidas', async () => {
    logar('USUARIO')
    const api = simularApi({
      'GET /avisos': { naoLidos: 2, itens: [aviso(2, 'Caminhão na portaria', 'Litoral · placa ABC1D23 · chegou às 08:15'), aviso(1, 'Agendamento pelo link do fornecedor', 'Beta')] },
      'POST /avisos/lidas': { naoLidos: 0, itens: [aviso(2, 'Caminhão na portaria', 'Litoral', true)] }
    })
    const { w } = await montar(SinoAvisos)
    expect(w.find('.request-bell-count').text()).toBe('2')
    await w.find('.request-bell-button').trigger('click')
    await flushPromises()
    expect(w.text()).toContain('chegou às 08:15')
    expect(w.findAll('.request-mini.nao-lida')).toHaveLength(2)
    await botao(w, 'Marcar lidas').trigger('click')
    await flushPromises()
    expect(api.ultima('POST /avisos/lidas')).toBeTruthy()
    expect(w.find('.request-bell-count').exists()).toBe(false)
  })
})

const cartao = (id, extra = {}) => ({ agendamentoId: id, fornecedor: 'Litoral Alimentos', placa: null, motorista: null, idPedido: 4500 + id, faixa: '08:00 - 10:00', tipoCaminhao: 'Truck', paletes: 12, codigoStatus: 'CONFIRMADO', ...extra })
const quadro = () => ({
  data: hoje(), cdId: 1,
  docas: [{ doca: { id: 1, nome: 'Doca 1' }, agendamentoId: null }],
  colunas: {
    PREVISTO: [cartao(1, { placa: 'ABC1D23', motorista: 'João' })],
    AGUARDANDO: [cartao(2, { placa: 'BRA2E19', codigoStatus: 'CHEGOU', chegadaEm: '2026-11-02T11:10:00Z' })],
    NA_DOCA: [], DESCARREGANDO: [],
    LIBERADO: [cartao(3, { placa: 'QWE1R23', codigoStatus: 'RECEBIDO' }), cartao(4, { placa: 'XYZ9K88', codigoStatus: 'RECEBIDO', saidaEm: '2026-11-02T15:00:00Z' })]
  },
  indicador: { docasAtivas: 1, docasOcupadas: 0 }
})

describe('portaria', () => {
  it('registra chegada com placa, motorista e hora; encaminha à doca e registra saída', async () => {
    logar('ADMIN')
    const api = simularApi({
      'GET /cds': [], 'GET /patio': quadro(),
      'PATCH /agendamentos/1/status': { id: 1, status: 'CHEGOU' },
      'PUT /agendamentos/2/doca': { docaId: 1 },
      'PATCH /agendamentos/3/saida': { saidaEm: '2026-11-02T16:00:00Z' }
    })
    const { w } = await montar(PortariaView)
    expect(w.text()).toContain('Aguardando chegada')
    expect(w.text()).toContain('Saíram')
    await botao(w, 'Registrar chegada').trigger('click')
    await flushPromises()
    const hora = document.querySelector('#chh')
    hora.value = '08:15'
    hora.dispatchEvent(new Event('input'))
    await botao(w, 'Confirmar chegada').trigger('click')
    await flushPromises()
    expect(api.ultima('PATCH /agendamentos/1/status').corpo).toEqual({ acao: 'CHEGADA', placa: 'ABC1D23', motorista: 'João', horaChegada: '08:15' })
    expect(document.body.textContent).toContain('Todos foram avisados')

    const sel = w.findAllComponents({ name: 'Select' }).find((s) => s.props('placeholder') === 'Encaminhar à doca')
    sel.vm.$emit('change', { value: 1 })
    await flushPromises()
    expect(api.ultima('PUT /agendamentos/2/doca').corpo).toEqual({ docaId: 1 })

    await botao(w, 'Registrar saída').trigger('click')
    await flushPromises()
    expect(api.ultima('PATCH /agendamentos/3/saida')).toBeTruthy()
  })

  it('usuário comum só consulta: sem botões de ação', async () => {
    logar('USUARIO')
    simularApi({ 'GET /cds': [], 'GET /patio': quadro() })
    const { w } = await montar(PortariaView)
    expect(w.text()).toContain('BRA2E19')
    expect(botao(w, 'Registrar chegada')).toBeUndefined()
    expect(botao(w, 'Registrar saída')).toBeUndefined()
  })

  it('filtra por placa', async () => {
    logar('ADMIN')
    simularApi({ 'GET /cds': [], 'GET /patio': quadro() })
    const { w } = await montar(PortariaView)
    await w.find('#ptb').setValue('bra 2e19')
    expect(w.text()).toContain('BRA2E19')
    expect(w.text()).not.toContain('ABC1D23')
  })
})

describe('vale-palete e centros de distribuição', () => {
  it('tela do vale não consulta /vales-palete/undefined quando outra rota abre', async () => {
    HTMLCanvasElement.prototype.getContext = () => ({ beginPath() {}, moveTo() {}, lineTo() {}, stroke() {}, clearRect() {} })
    logar('ADMIN')
    const api = simularApi({ 'GET /tipos-palete': [], 'GET /vales-palete/configuracao': {}, 'GET /vales-palete': [] })
    const comp = { render: () => null }
    const { router } = await montar(ValePaleteView, {
      rota: '/vales-palete/novo',
      rotas: [{ path: '/vales-palete/novo', name: 'vale-novo', component: comp }, { path: '/vales-palete/:id(\\d+)', name: 'vale', component: comp },
        { path: '/vales-palete', name: 'vales', component: comp }]
    })
    await router.push('/vales-palete')
    await flushPromises()
    expect(api.chamadas.map((c) => c.caminho)).not.toContain('/vales-palete/undefined')
  })

  it('cadastro de CD não duplica os limites: aponta para a tela única', async () => {
    logar('ADMIN')
    simularApi({ 'GET /cds': [{ id: 1, codigo: '801', nome: 'CD Mongaguá', cidade: 'Mongaguá', ativo: true }] })
    const { w } = await montar(CentrosDistribuicaoView)
    await flushPromises()
    expect(w.text()).toContain('CD Mongaguá')
    expect(w.text()).not.toContain('pal. · padrão geral')
  })

  it('leitura do código de barras na busca do topo abre o vale na devolução', async () => {
    logar('USUARIO')
    const api = simularApi({ 'GET /busca': [], 'GET /vales-palete/leitura': { id: 12, codigo: 'VP-20261102-0003' } })
    const { w, router } = await montar(BuscaTopo)
    const campo = w.find('#busca-topo-campo')
    await campo.setValue('KRILL-VP-20261102-0003')
    await campo.trigger('keydown', { key: 'Enter' })
    await flushPromises()
    expect(api.ultima('GET /vales-palete/leitura').params.codigo).toBe('KRILL-VP-20261102-0003')
    expect(router.currentRoute.value.fullPath).toBe('/vales-palete/12?devolver=1')
  })
})
