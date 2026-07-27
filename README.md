
# SwimTrack v1.0

Aplicação Android pessoal para acompanhamento competitivo de natação.

## Funcionalidades
- Perfil da atleta
- Escalão automático
- TAC Distritais / Zonais / Nacionais
- Integração Swimrankings
- Exportação WhatsApp
- Tema azul claro
- Disclaimer legal

## Roadmap
- v1.2 → recordes distritais e nacionais
- v1.3 → calendário + notificações

## WebApp

A versão WebApp está em `webapp/` e compila com Vite/React.

```bash
cd webapp
npm install
npm run build
```

O workflow `.github/workflows/webapp.yml` gera o artifact `SwimTrack-WebApp` e publica no GitHub Pages.
