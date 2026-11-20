# Contribuindo com o Artix License

Obrigado por querer ajudar! Qualquer contribuição é bem-vinda: correções no guia, exemplos em outras linguagens, ideias e código.

## Preparando o ambiente

Você vai precisar de **JDK 17+** e do **Gradle**.

```bash
git clone https://github.com/stumdev/artix-license-validation.git
cd artix-license-validation/minecraft-license-plugin
gradle build              # o jar sai em build/libs/
```

Para testar, coloque o jar na pasta `plugins/` de um servidor Paper 1.20 e configure a `licenseKey` com `/artixlicense set <chave>`.

## Fluxo

1. Faça um fork e crie uma branch: `git checkout -b minha-mudanca`.
2. Faça a mudança e confira que o plugin compila (`gradle build`).
3. Abra um Pull Request explicando o que mudou e por quê.

Use [Conventional Commits](https://www.conventionalcommits.org/pt-br/) nas mensagens (`feat:`, `fix:`, `docs:`, `chore:`).

## Boas práticas

- Documentação em português do Brasil.
- Exemplos devem validar de forma assíncrona, com cache e offline grace, como descrito no README.
- **Nunca commite** chaves de licença reais.
