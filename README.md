<div align="center">

<img src="docs/banner.svg" alt="Artix License" width="100%">

<br>

[![License: MIT](https://img.shields.io/badge/license-MIT-66E1F5.svg)](LICENSE)
![Java](https://img.shields.io/badge/Java-17-ED8B00?logo=openjdk&logoColor=white)
![Paper](https://img.shields.io/badge/Paper-1.20-F7CF0D)
![Gradle](https://img.shields.io/badge/Gradle-Kotlin%20DSL-02303A?logo=gradle&logoColor=white)
![API](https://img.shields.io/badge/API-REST%20%2B%20JSON-66E1F5)
[![PRs welcome](https://img.shields.io/badge/PRs-welcome-66E1F5.svg)](CONTRIBUTING.md)

**Guia e exemplo Java para validar licenças de plugins Minecraft pela API do Artix.**

[Início rápido](#início-rápido) · [Request e response](#request) · [Plugin de exemplo](#plugin-de-exemplo) · [Contribuir](CONTRIBUTING.md) · [Changelog](CHANGELOG.md)

</div>

---

## Sobre

Este repositório explica como **integrar o seu plugin Minecraft** (ou qualquer aplicativo) ao sistema de licenças do [Artix](https://github.com/stumdev/artix-project). Ele traz a referência da API, as regras de boa integração e um **plugin Paper pronto** em [`minecraft-license-plugin/`](minecraft-license-plugin) para você copiar.

## Destaques

- ✅ **Uma chamada só:** um `POST` com a `licenseKey` responde se a licença é válida.
- 🔒 **Vínculo por IP:** no primeiro uso a chave fica presa ao IP público do servidor, sem você enviar nada.
- 🧩 **Plugin de exemplo completo:** comandos, validação assíncrona, cache e tolerância a quedas da API.
- 🛟 **Offline grace:** se a API cair, o servidor continua funcionando por um tempo desde a última validação bem-sucedida.
- ☕ **Só Java padrão:** usa o `HttpClient` do Java 17, sem bibliotecas extras.

---

## Sumário

- [Início rápido](#início-rápido)
- [O que a API faz](#o-que-a-api-faz)
- [Request](#request)
- [Response](#response)
- [Regras importantes](#regras-importantes)
- [Plugin de exemplo](#plugin-de-exemplo)
- [Exemplo Java mínimo](#exemplo-java-mínimo)
- [FAQ](#faq)
- [Contribuindo](#contribuindo)
- [Licença](#licença)

## Início rápido

URL da API (produção):

```
https://artix.sideload.lol/api/license/validate
```

Teste pelo terminal:

```bash
curl -s -X POST \
  'https://artix.sideload.lol/api/license/validate' \
  -H 'Content-Type: application/json' \
  -d '{"licenseKey":"ARTIX-XXXX-XXXX-XXXX-XXXX","serverName":"MyServer","pluginVersion":"1.0.0"}'
```

## O que a API faz

- Valida se a `licenseKey` existe e está **ACTIVE**.
- Bloqueia chaves **REVOKED** ou **EXPIRED** e chaves com `expiresAt` vencido.
- Faz **vínculo pelo IP público do request** no primeiro uso (`boundIp`). Depois disso, a chave só valida a partir do **mesmo IP**.
- Registra o uso para auditoria.
- Aplica **limite de requisições por IP** (padrão: 100 a cada 15 minutos).

> O IP usado é o do **request** (headers do proxy/CDN). O seu plugin **não precisa** (e não deve) enviar `serverIp`.

## Request

Body mínimo:

```json
{
  "licenseKey": "ARTIX-XXXX-XXXX-XXXX-XXXX"
}
```

Com metadados opcionais (recomendado):

```json
{
  "licenseKey": "ARTIX-XXXX-XXXX-XXXX-XXXX",
  "serverName": "MyServer",
  "pluginVersion": "1.0.0"
}
```

## Response

Licença válida (HTTP 200):

```json
{
  "valid": true,
  "message": "Licença válida",
  "license": {
    "key": "ARTIX-XXXX-XXXX-XXXX-XXXX",
    "plugin": "PluginName",
    "version": "1.0.0",
    "maxActivations": 1,
    "currentActivations": 1,
    "expiresAt": null
  }
}
```

Licença inválida (HTTP 200):

```json
{
  "valid": false,
  "message": "Licença não encontrada"
}
```

Erros HTTP:

| Código | Quando |
|---|---|
| `400` | parâmetros inválidos (formato de chave inválido, IP público do request indeterminado) |
| `429` | limite de requisições atingido |
| `500` | erro interno |

## Regras importantes

- **Valide de forma assíncrona**: nunca bloqueie a thread principal do servidor.
- **Use cache** (por exemplo, 5 a 10 minutos). Não valide em loop.
- **Implemente offline grace** (por exemplo, 30 a 60 minutos): se a API estiver fora, mantenha o plugin ativo por um tempo desde a última validação bem-sucedida.
- **Não exponha a chave** no chat ou nos logs para jogadores.
- **Não deixe a chave fixa no jar**: leia de `config.yml`.

## Plugin de exemplo

Em [`minecraft-license-plugin/`](minecraft-license-plugin): um plugin Paper 1.20 (Java 17) de um único arquivo, com:

- Comandos `/artixlicense set <chave>`, `/artixlicense validate` e `/artixlicense status` (permissão `artixlicense.admin`, padrão: OP).
- Validação assíncrona com timeout.
- Cache do último resultado em `cache.yml`.
- Offline grace configurável.

Configuração (`config.yml`):

```yaml
apiUrl: "https://artix.sideload.lol/api/license/validate"
licenseKey: ""
serverName: "MyServer"
pluginVersion: "1.0.0"
cacheMinutes: 10          # evita validar a cada reinício
offlineGraceMinutes: 60   # tolerância se a API cair
debug: false
```

Para compilar:

```bash
cd minecraft-license-plugin
gradle build              # o jar sai em build/libs/
```

## Exemplo Java mínimo

```java
HttpClient client = HttpClient.newHttpClient();
String json = "{\"licenseKey\":\"" + licenseKey + "\",\"serverName\":\"" + serverName + "\",\"pluginVersion\":\"" + version + "\"}";

HttpRequest req = HttpRequest.newBuilder()
  .uri(URI.create("https://artix.sideload.lol/api/license/validate"))
  .header("Content-Type", "application/json")
  .POST(HttpRequest.BodyPublishers.ofString(json))
  .build();

HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
boolean valid = res.statusCode() == 200 && res.body().replaceAll("\\s+", "").contains("\"valid\":true");
```

## FAQ

**Meu servidor mudou de IP e a licença parou.**
É esperado: a licença fica vinculada ao primeiro IP (`boundIp`). Peça o **reset do IP** no painel do Artix e valide de novo para vincular o novo endereço.

**Estou atrás de proxy, vai validar?**
Sim, desde que o proxy/CDN repasse o IP público corretamente (`X-Forwarded-For`, `X-Real-IP` ou `CF-Connecting-IP`).

## Contribuindo

Contribuições são bem-vindas! Leia o [guia de contribuição](CONTRIBUTING.md) e o [código de conduta](CODE_OF_CONDUCT.md). Para falhas de segurança, veja a [política de segurança](SECURITY.md).

## Licença

Distribuído sob a licença [MIT](LICENSE).
