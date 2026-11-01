import { afterEach, describe, expect, it } from 'vitest'
import { erroHttp, logar, montar, sair, simularApi, PERMISSOES } from '../montar'
import InicioView from '../../src/views/InicioView.vue'
import { menuItens } from '../../src/menu'

describe('InicioView e menu único', () => {
  afterEach(sair)

  it('home é o painel do dia, sem uma segunda navegação', async () => {
    logar('ADMIN')
    simularApi({ 'GET /dashboard': erroHttp(500, 'fora') })
    const { w } = await montar(InicioView)
    expect(w.find('h1').text()).toContain('Hoje')
    expect(w.find('.central-card').exists()).toBe(false)
  })

  it('admin vê os cinco grupos, sem itens repetidos', () => {
    const m = menuItens(() => true)
    expect(m.map((g) => g.grupo)).toEqual(['Agendamentos', 'Recebimento', 'Paletes', 'Cadastros', 'Acessos e auditoria'])
    expect(m[0].itens.map((i) => i.label)).toEqual(['Ocupação da semana', 'Agendamentos', 'Novo agendamento', 'Links de agendamento', 'Relatório'])
    const rotas = m.flatMap((g) => g.itens.map((i) => i.to))
    expect(new Set(rotas).size).toBe(rotas.length)
    const rotulos = m.flatMap((g) => g.itens.map((i) => i.label))
    expect(new Set(rotulos).size).toBe(rotulos.length)
  })

  it('usuário comum não vê novo agendamento, cadastros nem acessos', () => {
    const consulta = (c) => PERMISSOES.USUARIO.includes(c)
    const grupos = menuItens(consulta).map((g) => g.grupo)
    expect(grupos).not.toContain('Cadastros')
    expect(grupos).not.toContain('Acessos e auditoria')
    expect(menuItens(consulta).flatMap((g) => g.itens.map((i) => i.to))).not.toContain('/agendamentos/novo')
    const portaria = (c) => [1102, 1204, 1205].includes(c)
    expect(menuItens(portaria).flatMap((g) => g.itens.map((i) => i.to))).toEqual(['/agendamentos', '/patio'])
  })
})
