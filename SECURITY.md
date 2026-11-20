# Política de Segurança

## Reportando uma vulnerabilidade

Se encontrou uma falha de segurança no exemplo ou na API de licenças do Artix, **não abra uma issue pública**. Use o recurso [Report a vulnerability](https://github.com/stumdev/artix-license-validation/security/advisories/new) do GitHub e descreva o problema com o máximo de detalhes possível (sem incluir chaves reais de ninguém).

## Boas práticas na sua integração

- Leia a chave do `config.yml`; nunca a deixe fixa dentro do jar.
- Não mostre a chave no chat nem em logs visíveis para jogadores.
- Valide de forma assíncrona, com cache e offline grace, para não travar o servidor nem sobrecarregar a API.
- Se o servidor estiver atrás de proxy/CDN, garanta que ele repasse o IP público (`X-Forwarded-For`, `X-Real-IP` ou `CF-Connecting-IP`).
