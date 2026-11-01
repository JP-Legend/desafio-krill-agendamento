import { afterEach, describe, expect, it } from 'vitest'
import { erroHttp, flushPromises, logar, montar, sair, simularApi } from '../montar'
import { guarda, routes } from '../../src/router'
import { menuItens } from '../../src/menu'
import LinksAgendamentoView from '../../src/views/LinksAgendamentoView.vue'
import AgendarView from '../../src/views/AgendarView.vue'
import LojasView from '../../src/views/LojasView.vue'

const DESTINOS = [
  { cdId: null, loja: null, descricao: 'Todos os CDs (fornecedor escolhe)' },
  { cdId: 3, loja: null, descricao: 'CD 801 · CD 801' },
  { cdId: 4, loja: null, descricao: 'CD 805 · CD 805' },
  { cdId: 1, loja: null, descricao: 'CD 806 · CD Rede Krill' },
  { cdId: null, loja: 'Loja Centro', descricao: 'Loja Centro' }
]
const LINKS = [
  { id: 2, destino: 'CD 806 · CD Rede Krill', cdId: 1, loja: null, url: 'http://localhost:8081/agendar/abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG', ativo: true, criadoPor: 'admin', criadoEm: '2026-11-01T12:00:00Z', revogadoPor: null, revogadoEm: null },
  { id: 1, destino: 'Loja Centro', cdId: null, loja: 'Loja Centro', url: null, ativo: false, criadoPor: 'admin', criadoEm: '2026-10-30T12:00:00Z', revogadoPor: 'admin', revogadoEm: '2026-10-31T12:00:00Z' }
]
const hoje = new Date().toISOString().slice(0, 10)
const DIAS = [{ data: hoje, capacidade: 480, paletesOcupados: 400, percentual: 83, excedente: 0, quantidadeAgendamentos: 3, faixas: [
  { faixaHorariaId: 1, descricao: '08:00 - 10:00', capacidade: 120, paletesOcupados: 110, percentual: 92, excedente: 0 },
  { faixaHorariaId: 2, descricao: '10:00 - 12:00', capacidade: 120, paletesOcupados: 0, percentual: 0, excedente: 0 }
] }]
const FORM = { destino: 'CD 806 · CD Rede Krill', loja: null, cds: [{ id: 1, nome: 'CD 806 · CD Rede Krill' }], tiposCaminhao: [{ id: 2, nome: 'Truck' }], tiposPaletizacao: [{ id: 1, nome: 'Paletizado' }] }
const botao = (w, texto) => w.findAll('button').find((b) => b.text().includes(texto))
const docBotao = (texto) => [...document.querySelectorAll('button')].findLast((b) => b.textContent.includes(texto))

describe('links de agendamento (porte do Gestão CD)', () => {
  afterEach(sair)

  it('só o administrador vê a tela e o item de menu; usuário comum não', () => {
    const r = routes.find((x) => x.path === '/links-agendamento')
    const rota = { name: r.name, meta: r.meta, fullPath: r.path }
    logar('USUARIO')
    expect(guarda(rota)).not.toBe(true)
    expect(JSON.stringify(menuItens())).not.toContain('/links-agendamento')
    logar('ADMIN')
    expect(guarda(rota)).toBe(true)
    expect(JSON.stringify(menuItens())).toContain('/links-agendamento')
  })

  it('lista links, gera para o destino escolhido e revoga com confirmação', async () => {
    logar('ADMIN')
    const api = simularApi({ 'GET /links-agendamento/destinos': DESTINOS, 'GET /links-agendamento': LINKS, 'POST /links-agendamento': LINKS[0], 'PATCH /links-agendamento/2/revogacao': { ...LINKS[0], ativo: false } })
    const { w, toast } = await montar(LinksAgendamentoView)
    const texto = w.find('.tabela-links').text()
    expect(texto).toContain('CD 806 · CD Rede Krill')
    expect(texto).toContain('desativado')
    expect(w.find('.tabela-links input').element.value).toContain('/agendar/')

    await botao(w, 'Gerar / regenerar link').trigger('click')
    await flushPromises()
    expect(document.body.textContent).toContain('O link ativo deste destino deixa de funcionar')
    docBotao('Gerar link').click()
    await flushPromises()
    expect(api.ultima('POST /links-agendamento').corpo).toEqual({ cdId: null, loja: null })
    expect(toast()).toContain('Novo link público gerado')

    await botao(w, 'Revogar').trigger('click')
    await flushPromises()
    docBotao('Revogar').click()
    await flushPromises()
    expect(api.ultima('PATCH /links-agendamento/2/revogacao')).toBeTruthy()
    expect(toast()).toContain('Link revogado.')
  })

  it('formulário público agenda com alerta de excesso sem bloquear e mostra o link do agendamento', async () => {
    const api = simularApi({
      'GET /publico/agendar/tk': FORM,
      'GET /publico/agendar/tk/disponibilidade': DIAS,
      'POST /publico/agendar/tk': { id: 55, data: hoje, faixaHoraria: '08:00 - 10:00', destino: 'CD 806 · CD Rede Krill', paletesEquivalentes: 30, alertas: ['Faixa 08:00 - 10:00 excedida: 140 de 120 paletes'], linkFornecedor: 'http://localhost:8081/fornecedor/segredo' }
    })
    const { w, tela, toast } = await montar(AgendarView, { rota: '/agendar/tk' })
    expect(w.text()).toContain('CD 806 · CD Rede Krill')
    expect(w.text()).not.toContain('quantidadeAgendamentos')
    await w.find('form').trigger('submit')
    await flushPromises()
    expect(toast()).toContain('Informe o dia e a faixa')
    expect(api.chamadas.some((c) => c.chave === 'POST /publico/agendar/tk')).toBe(false)

    Object.assign(tela.vm.form, { fornecedor: 'Laticínios Serra Azul', emailFornecedor: 'agenda@serraazul.com.br', idPedido: 77001, tipoCaminhaoId: 2, tipoPaletizacaoId: 1, quantidadePaletes: 30 })
    await flushPromises()
    await w.findAll('button.faixa')[0].trigger('click')
    expect(w.find('.alerta.aviso').text()).toContain('O agendamento é aceito')
    await w.find('form').trigger('submit')
    await flushPromises()
    const corpo = api.ultima('POST /publico/agendar/tk').corpo
    expect(corpo).toMatchObject({ cdId: 1, faixaHorariaId: 1, data: hoje, quantidadePaletes: 30, cnpj: null, siteWeb: '' })
    expect(w.text()).toContain('Agendamento nº 55 enviado')
    expect(w.text()).toContain('excedida')
    expect(w.find('.link-copia input').element.value).toBe('http://localhost:8081/fornecedor/segredo')
  })

  it('link revogado mostra aviso claro', async () => {
    simularApi({ 'GET /publico/agendar/velho': erroHttp(404, 'Link não encontrado(a): desativado') })
    const { w } = await montar(AgendarView, { rota: '/agendar/velho' })
    expect(w.text()).toContain('Este link de agendamento foi desativado. Peça um novo link ao CD.')
  })

  it('select lista todos os CDs e lojas e gera o link da loja escolhida', async () => {
    logar('ADMIN')
    const api = simularApi({ 'GET /links-agendamento/destinos': DESTINOS, 'GET /links-agendamento': [], 'POST /links-agendamento': LINKS[0] })
    const { w, tela } = await montar(LinksAgendamentoView)
    await w.find('#la-destino').trigger('click')
    await flushPromises()
    const opcoes = [...document.querySelectorAll('[role=option]')].map((o) => o.textContent)
    expect(opcoes).toEqual(['Todos os CDs (fornecedor escolhe)', 'CD 801 · CD 801', 'CD 805 · CD 805', 'CD 806 · CD Rede Krill', 'Loja Centro'])
    tela.vm.destino = DESTINOS.length - 1
    await flushPromises()
    expect(w.find('.campo-destino').text()).toContain('Loja Centro')
    await botao(w, 'Gerar / regenerar link').trigger('click')
    await flushPromises()
    docBotao('Gerar link').click()
    await flushPromises()
    expect(api.ultima('POST /links-agendamento').corpo).toEqual({ cdId: null, loja: 'Loja Centro' })
  })

  it('cadastro de lojas inclui loja com o CD que abastece', async () => {
    logar('ADMIN')
    const api = simularApi({ 'GET /cds': [{ id: 1, codigo: '806', nome: 'CD Rede Krill', ativo: true }], 'GET /lojas': [{ id: 1, nome: 'Loja 01', cdId: 1, ativo: true }], 'POST /lojas': { id: 2 } })
    const { w } = await montar(LojasView)
    expect(w.text()).toContain('Loja 01')
    expect(w.text()).toContain('CD 806')
    await w.findAll('button').find((b) => b.text().includes('Nova loja')).trigger('click')
    await flushPromises()
    const nome = document.querySelector('#nome')
    nome.value = 'Loja 04 - Centro'
    nome.dispatchEvent(new Event('input'))
    docBotao('Salvar').click()
    await flushPromises()
    expect(api.ultima('POST /lojas').corpo).toMatchObject({ nome: 'Loja 04 - Centro', ativo: true })
  })
})
