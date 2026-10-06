# JARVIS Mobile IA 🤖

Assistente de IA local para Android, pensado para executar tarefas no celular com autorização do usuário.

## Objetivos
- Interface de assistente por texto e voz.
- Automação Android por Accessibility Service, somente com autorização explícita.
- Arquitetura preparada para inferência local/offline.
- Nenhuma chave de API é necessária para a arquitetura local.
- Ações sensíveis devem pedir confirmação.

## APK
O workflow em `.github/workflows/jarvis-android.yml` compila o APK de debug e publica o artefato no GitHub Actions.

## Logo
`jarvis-mobile-ia/logo.svg`

> Um modelo local grande não é armazenado no Git por causa do tamanho. A etapa de build pode baixar um modelo compatível de uma fonte autorizada quando essa integração for configurada.
