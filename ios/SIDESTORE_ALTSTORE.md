# SwimTrack iOS — SideStore / AltStore

Esta variante cria um IPA sem assinatura no GitHub Actions. O SideStore/AltStore volta a assinar o IPA com a conta Apple do utilizador no momento da instalação.

## GitHub
1. Carrega este projeto para o repositório.
2. Actions > Build iOS IPA - SideStore AltStore > Run workflow.
3. No fim, abre o artifact `SwimTrack-iOS-SideStore-AltStore` e obtém o IPA.

## SideStore / AltStore
Importa o IPA na aplicação e instala-o. Em contas Apple gratuitas, a assinatura de sideload tem validade limitada e a app precisa de ser renovada periodicamente pela solução de sideload.

Bundle ID: `pt.rjp.swimtrack`
Versão iOS: 2.1.0
Deployment target: iOS 16+
