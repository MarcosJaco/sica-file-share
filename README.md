# SiCA - Sistema de Compartilhamento de Arquivos

Trabalho da disciplina de Desenvolvimento de Software Cliente-Servidor (ADS - PUC Goiás).

Aplicação cliente/servidor em Java usando sockets TCP. O cliente conecta no servidor e pode enviar arquivos, listar os arquivos que estão no servidor e baixar algum deles.

## Como rodar

Compilar:

```
javac *.java
```

Iniciar o servidor (porta 5000):

```
java ServidorSiCA
```

Em outro terminal, iniciar o cliente:

```
java ClienteSiCA
```

Ao abrir o cliente é só informar o IP do servidor (ou dar Enter para usar localhost) e escolher uma opção no menu:

```
1 - Enviar arquivo
2 - Listar arquivos do servidor
3 - Baixar arquivo
0 - Sair
```

Os arquivos enviados ficam na pasta `arquivos_servidor` e os baixados vão para a pasta `downloads`. As duas são criadas automaticamente.
