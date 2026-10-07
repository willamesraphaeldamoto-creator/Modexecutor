# Man Checkout

Gateway de pagamentos em Expo/React Native com Firebase Auth, Pix/Krypt, cripto, saques e checkout personalizado.

## GitHub Actions

O workflow `build-apk.yml` gera o APK pelo GitHub Actions sem conta Expo/EAS. As credenciais dos gateways devem ficar no backend/Netlify, nunca no APK.

## Backend

A pasta `netlify/functions` contém o proxy para Krypt Gateway e Abacate Pay.
