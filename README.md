# Integração DRM — Validação de Licença (para devs de plugins)

Este guia explica como **integrar seu plugin Minecraft** (ou qualquer app) com o sistema de licenças do Artix.

## URL da API (produção)

- `https://artix.sideload.lol/api/license/validate`

## O que a API faz (resumo)

- Valida se a `licenseKey` existe e está **ACTIVE**.
- Bloqueia chaves **REVOKED**/**EXPIRED** e chaves com `expiresAt` vencido.
- Faz **bind por IP público do request** no primeiro uso (`boundIp`).
  - Depois disso, a key só valida a partir do **mesmo IP**.
- Registra o uso (telemetria) para auditoria.
- Aplica **rate limit por IP** (padrão 100 req / 15 min).

> Importante: o IP usado é o do **request** (headers do proxy/CDN). Seu plugin **não precisa** (e não deve) enviar `serverIp`.

## Request

### Body mínimo

```json
{
  "licenseKey": "ARTIX-XXXX-XXXX-XXXX-XXXX"
}
```

### Metadados opcionais (recomendado)

```json
{
  "licenseKey": "ARTIX-XXXX-XXXX-XXXX-XXXX",
  "serverName": "MyServer",
  "pluginVersion": "1.0.0"
}
```

## Response

### Licença válida (HTTP 200)

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

### Licença inválida (HTTP 200)

```json
{
  "valid": false,
  "message": "Licença não encontrada"
}
```

### Erros HTTP

- `400` parâmetros inválidos (ex.: formato de key inválido, não foi possível determinar IP público do request)
- `429` rate limit
- `500` erro interno

## Regras importantes (pra não dar dor de cabeça)

- **Faça a validação de forma assíncrona** (não bloqueie a main thread do servidor).
- **Use cache** (ex.: 5–10 minutos). Não valide em loop.
- **Implemente offline grace** (ex.: 30–60 minutos):
  - se a API estiver fora, você pode manter o plugin ativo por um tempo desde a última validação bem-sucedida.
- **Não exponha a key** no chat/log para jogadores.
- **Não hardcode a key no jar**: leia de `config.yml`.

## Exemplo pronto (Paper/Spigot)

Existe um exemplo simples (um único arquivo Java) no repositório:

- [examples/minecraft-license-plugin/src/main/java/com/artix/example/ArtixLicensePlugin.java](examples/minecraft-license-plugin/src/main/java/com/artix/example/ArtixLicensePlugin.java)
- [examples/minecraft-license-plugin/src/main/resources/config.yml](examples/minecraft-license-plugin/src/main/resources/config.yml)

Ele já vem com:

- Comandos para setar/validar key
- Cache em `cache.yml`
- Offline grace

## Exemplo Java (snippet minimalista)

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

## Teste rápido via cURL

```bash
curl -s -X POST \
  'https://artix.sideload.lol/api/license/validate' \
  -H 'Content-Type: application/json' \
  -d '{"licenseKey":"ARTIX-XXXX-XXXX-XXXX-XXXX","serverName":"MyServer","pluginVersion":"1.0.0"}'
```

## FAQ

### “Meu servidor mudou de IP e a licença parou”
Isso é esperado: a licença fica vinculada ao primeiro IP (`boundIp`). É necessário **resetar a ativação** no painel/admin e validar novamente para fazer bind no novo IP.

### “Estou atrás de proxy, vai validar?”
Sim, desde que o proxy/CDN passe o IP público corretamente (`X-Forwarded-For`, `X-Real-IP`, `CF-Connecting-IP`).

---

Para detalhes completos das regras server-side, veja:

- [LICENSE_VALIDATION.md](LICENSE_VALIDATION.md)
