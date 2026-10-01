import java.io.*;
import java.net.Socket;
import java.util.Scanner;

/*
 * SiCA - Sistema de Compartilhamento de Arquivos (lado do CLIENTE)
 *
 * Como funciona:
 * O cliente pede o IP do servidor (se deixar em branco usa localhost),
 * conecta na porta 5000 e mostra um menu com as opcoes:
 *   1 - Enviar arquivo
 *   2 - Listar arquivos do servidor
 *   3 - Baixar arquivo
 *   0 - Sair
 *
 * Pra cada opcao o cliente manda o comando pro servidor (ENVIAR, LISTAR,
 * BAIXAR ou SAIR) e depois segue o mesmo protocolo que o servidor espera
 * (nome -> tamanho -> bytes do arquivo).
 *
 * Os arquivos baixados sao salvos na pasta "downloads" que fica onde o
 * programa esta sendo executado.
 */
public class ClienteSiCA {

    static final int PORTA = 5000;
    static final String PASTA_DOWNLOADS = "downloads";

    static DataInputStream entrada;
    static DataOutputStream saida;
    static Scanner teclado = new Scanner(System.in);

    public static void main(String[] args) {
        System.out.print("IP do servidor (Enter para localhost): ");
        String ip = teclado.nextLine().trim();
        if (ip.isEmpty()) {
            ip = "localhost";
        }

        try (Socket socket = new Socket(ip, PORTA)) {
            System.out.println("Conectado ao servidor " + ip + ":" + PORTA);

            entrada = new DataInputStream(socket.getInputStream());
            saida = new DataOutputStream(socket.getOutputStream());

            int opcao = -1;
            while (opcao != 0) {
                mostrarMenu();
                opcao = lerOpcao();

                switch (opcao) {
                    case 1:
                        enviarArquivo();
                        break;
                    case 2:
                        listarArquivos();
                        break;
                    case 3:
                        baixarArquivo();
                        break;
                    case 0:
                        saida.writeUTF("SAIR");
                        saida.flush();
                        System.out.println("Encerrando...");
                        break;
                    default:
                        System.out.println("Opcao invalida!");
                }
            }
        } catch (IOException e) {
            System.out.println("Nao foi possivel conectar/comunicar com o servidor: " + e.getMessage());
        }
    }

    static void mostrarMenu() {
        System.out.println();
        System.out.println("===== SiCA - Menu =====");
        System.out.println("1 - Enviar arquivo");
        System.out.println("2 - Listar arquivos do servidor");
        System.out.println("3 - Baixar arquivo");
        System.out.println("0 - Sair");
        System.out.print("Escolha: ");
    }

    /*
     * Le a opcao digitada. Se o usuario digitar algo que nao e numero
     * retorna -1 (que cai no "Opcao invalida") em vez de dar erro.
     */
    static int lerOpcao() {
        String linha = teclado.nextLine().trim();
        try {
            return Integer.parseInt(linha);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /*
     * Envia um arquivo local para o servidor.
     *
     * Pede o caminho do arquivo, verifica se ele existe e manda:
     * comando "ENVIAR" -> nome do arquivo -> tamanho -> conteudo.
     * Depois espera o "OK" do servidor confirmando que recebeu tudo.
     */
    static void enviarArquivo() throws IOException {
        System.out.print("Caminho do arquivo: ");
        String caminho = teclado.nextLine().trim();

        // tira aspas caso o usuario tenha copiado o caminho do explorer
        caminho = caminho.replace("\"", "");

        File arquivo = new File(caminho);
        if (!arquivo.exists() || !arquivo.isFile()) {
            System.out.println("Arquivo nao encontrado!");
            return;
        }

        saida.writeUTF("ENVIAR");
        saida.writeUTF(arquivo.getName());
        saida.writeLong(arquivo.length());

        FileInputStream fis = new FileInputStream(arquivo);
        byte[] buffer = new byte[4096];
        int lidos;
        while ((lidos = fis.read(buffer)) != -1) {
            saida.write(buffer, 0, lidos);
        }
        fis.close();
        saida.flush();

        String resposta = entrada.readUTF();
        if (resposta.equals("OK")) {
            System.out.println("Arquivo \"" + arquivo.getName() + "\" enviado com sucesso! (" + arquivo.length() + " bytes)");
        } else {
            System.out.println("Servidor respondeu: " + resposta);
        }
    }

    /*
     * Pede a lista de arquivos para o servidor e mostra na tela.
     *
     * O servidor manda primeiro a quantidade e depois nome + tamanho de
     * cada arquivo, entao e so fazer um for com essa quantidade.
     */
    static void listarArquivos() throws IOException {
        saida.writeUTF("LISTAR");
        saida.flush();

        int qtd = entrada.readInt();
        if (qtd == 0) {
            System.out.println("Nenhum arquivo no servidor.");
            return;
        }

        System.out.println("Arquivos no servidor (" + qtd + "):");
        for (int i = 0; i < qtd; i++) {
            String nome = entrada.readUTF();
            long tamanho = entrada.readLong();
            System.out.println("  " + (i + 1) + ". " + nome + " - " + tamanho + " bytes");
        }
    }

    /*
     * Baixa um arquivo do servidor.
     *
     * Manda "BAIXAR" e o nome do arquivo. Se o servidor responder "OK" le o
     * tamanho e vai recebendo os bytes ate completar, salvando na pasta
     * downloads. Se responder "NAO_ENCONTRADO" so avisa o usuario.
     */
    static void baixarArquivo() throws IOException {
        System.out.print("Nome do arquivo para baixar: ");
        String nome = teclado.nextLine().trim();
        if (nome.isEmpty()) {
            System.out.println("Nome vazio.");
            return;
        }

        saida.writeUTF("BAIXAR");
        saida.writeUTF(nome);
        saida.flush();

        String resposta = entrada.readUTF();
        if (!resposta.equals("OK")) {
            System.out.println("Arquivo nao encontrado no servidor.");
            return;
        }

        long tamanho = entrada.readLong();

        File pasta = new File(PASTA_DOWNLOADS);
        if (!pasta.exists()) {
            pasta.mkdir();
        }
        File destino = new File(pasta, new File(nome).getName());
        FileOutputStream fos = new FileOutputStream(destino);

        byte[] buffer = new byte[4096];
        long recebido = 0;
        int lidos;
        while (recebido < tamanho) {
            int qtd = (int) Math.min(buffer.length, tamanho - recebido);
            lidos = entrada.read(buffer, 0, qtd);
            if (lidos == -1) {
                break;
            }
            fos.write(buffer, 0, lidos);
            recebido += lidos;
        }
        fos.close();

        System.out.println("Download concluido! Salvo em: " + destino.getPath() + " (" + recebido + " bytes)");
    }
}
