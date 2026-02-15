# Artix License Example (Paper/Spigot)

Exemplo **bem simples** (um único arquivo Java) que:

- Lê a key de `plugins/ArtixLicenseExample/config.yml`
- Valida em `POST /api/license/validate`
- Salva cache em `plugins/ArtixLicenseExample/cache.yml` para evitar spam
- Implementa *offline grace* (se a API cair, o servidor não cai instantaneamente)

## Como usar

1) Build:

- Requisitos: Gradle instalado **ou** gerar o wrapper na sua máquina.
- Build com Gradle:
	- `gradle build`
	- O jar sai em `build/libs/artix-license-plugin-1.0.0.jar` (shadowJar)

2) Instale no seu servidor:

- Coloque o jar em `plugins/`
- Inicie o servidor uma vez

3) Configure a licença:

- Edite `plugins/ArtixLicenseExample/config.yml` e coloque sua `licenseKey`
- Ou use: `/artixlicense set ARTIX-XXXX-XXXX-XXXX-XXXX`

4) Reinicie ou rode:

- `/artixlicense validate`

## O que o backend espera

O plugin faz `POST` para:

- `https://yourdomain.com/api/license/validate`

Payload (mínimo):

- `licenseKey`

Payload (opcional):

- `serverName`
- `pluginVersion`

> A API faz o bind usando o IP público do **request** (headers do proxy/CDN). Não envie `serverIp`.
