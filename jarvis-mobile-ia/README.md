# JARVIS Mobile IA 🤖

Assistente de IA local para Android, pensado para executar tarefas no celular com autorização do usuário.

## Objetivos
- Interface de assistente por texto e voz.
- Automação Android por Accessibility Service, somente com autorização explícita.
- Arquitetura preparada para inferência local/offline.
- Nenhuma chave de API é necessária para a arquitetura local.
- Ações sensíveis devem pedir confirmação.

## APK
O workflow em `.github/workflows/jarvis-mobile-ia.yml` compila o APK de debug e publica o artefato no GitHub Actions.

## Modelo local
O app inclui um botão para baixar o Qwen2.5 0.5B Instruct em GGUF para o armazenamento privado do aplicativo. O download do modelo não usa uma API de inferência.

## Logo
`jarvis-mobile-ia/logo.svg`

> O motor de inferência local ainda é uma etapa separada; o APK atual prepara o modelo e a interface para essa integração.
