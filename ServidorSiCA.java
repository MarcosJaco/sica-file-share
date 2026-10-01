import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

/*
 * SiCA - Sistema de Compartilhamento de Arquivos (lado do SERVIDOR)
 *
 * Como funciona:
 * O servidor fica escutando na porta 5000 esperando os clientes se conectarem.
 * Quando um cliente conecta, e criada uma Thread so pra ele, assim o servidor
 * consegue atender mais de um cliente ao mesmo tempo.
 *
 * Os arquivos ficam guardados na pasta "arquivos_servidor" (se ela nao existir
 * o proprio programa cria).
 *
 * A comunicacao e feita com DataInputStream e DataOutputStream. O cliente
 * sempre manda primeiro uma String com o comando, que pode ser:
 *   ENVIAR  -> cliente vai mandar um arquivo pro servidor
 *   LISTAR  -> servidor devolve a lista de arquivos da pasta
 *   BAIXAR  -> servidor manda um arquivo pro cliente
 *   SAIR    -> encerra a conexao
 *
 * Pra mandar um arquivo (nos dois sentidos) o "protocolo" e sempre o mesmo:
 * primeiro vai o nome (writeUTF), depois o tamanho em bytes (writeLong) e
 * depois os bytes do arquivo em pedacos de 4KB.
 */
public class ServidorSiCA {

    static final int PORTA = 5000;
    static final String PASTA = "arquivos_servidor";

    public static void main(String[] args) {
        File pasta = new File(PASTA);
        if (!pasta.exists()) {
            pasta.mkdir();
        }

        try (ServerSocket servidor = new ServerSocket(PORTA)) {
            System.out.println("Servidor SiCA iniciado na porta " + PORTA);
            System.out.println("Pasta dos arquivos: " + pasta.getAbsolutePath());
            System.out.println("Aguardando conexoes...");

            // loop infinito, o servidor fica sempre esperando novos clientes
            while (true) {
                Socket cliente = servidor.accept();
                System.out.println("Cliente conectado: " + cliente.getInetAddress().getHostAddress());

                // cada cliente e atendido em uma thread separada
                Thread t = new Thread(new AtendeCliente(cliente));
                t.start();
            }
        } catch (IOException e) {
            System.out.println("Erro no servidor: " + e.getMessage());
        }
    }
}

/*
 * Classe responsavel por atender UM cliente.
 * Implementa Runnable pra poder rodar dentro de uma Thread.
 */
class AtendeCliente implements Runnable {

    private Socket socket;
    private DataInputStream entrada;
    private DataOutputStream saida;

    public AtendeCliente(Socket socket) {
        this.socket = socket;
    }

    /*
     * Metodo principal da thread.
     * Fica lendo os comandos que o cliente manda e chama o metodo certo
     * pra cada um. Sai do loop quando o cliente manda SAIR ou quando a
     * conexao cai.
     */
    @Override
    public void run() {
        String ip = socket.getInetAddress().getHostAddress();
        try {
            entrada = new DataInputStream(socket.getInputStream());
            saida = new DataOutputStream(socket.getOutputStream());

            boolean continuar = true;
            while (continuar) {
                String comando = entrada.readUTF();
                System.out.println("[" + ip + "] comando recebido: " + comando);

                switch (comando) {
                    case "ENVIAR":
                        receberArquivo();
                        break;
                    case "LISTAR":
                        listarArquivos();
                        break;
                    case "BAIXAR":
                        enviarArquivo();
                        break;
                    case "SAIR":
                        continuar = false;
                        break;
                    default:
                        saida.writeUTF("ERRO: comando desconhecido");
                        saida.flush();
                }
            }
        } catch (EOFException e) {
            // acontece quando o cliente fecha a conexao sem mandar SAIR
            System.out.println("[" + ip + "] cliente encerrou a conexao");
        } catch (IOException e) {
            System.out.println("[" + ip + "] erro: " + e.getMessage());
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                // nao tem muito o que fazer aqui
            }
            System.out.println("[" + ip + "] desconectado");
        }
    }

    /*
     * Recebe um arquivo enviado pelo cliente (comando ENVIAR).
     *
     * Le o nome do arquivo e o tamanho, depois vai lendo os bytes do socket
     * e gravando no disco ate completar o tamanho informado.
     * No final responde "OK" pro cliente saber que deu certo.
     *
     * Obs: uso o new File(nome).getName() pra pegar so o nome do arquivo,
     * assim se alguem mandar algo tipo "../../teste.txt" ele nao salva fora
     * da pasta do servidor.
     */
    private void receberArquivo() throws IOException {
        String nome = new File(entrada.readUTF()).getName();
        long tamanho = entrada.readLong();

        File arquivo = new File(ServidorSiCA.PASTA, nome);
        FileOutputStream fos = new FileOutputStream(arquivo);

        byte[] buffer = new byte[4096];
        long recebido = 0;
        int lidos;

        // le no maximo o que falta, pra nao pegar bytes do proximo comando
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

        System.out.println("Arquivo recebido: " + nome + " (" + recebido + " bytes)");
        saida.writeUTF("OK");
        saida.flush();
    }

    /*
     * Lista os arquivos que estao na pasta do servidor (comando LISTAR).
     *
     * Manda primeiro a quantidade de arquivos (writeInt) e depois, pra cada
     * um, o nome e o tamanho. Assim o cliente sabe quantas vezes tem que ler.
     */
    private void listarArquivos() throws IOException {
        File pasta = new File(ServidorSiCA.PASTA);
        File[] arquivos = pasta.listFiles();

        // conta so os arquivos, ignorando subpastas
        int qtd = 0;
        if (arquivos != null) {
            for (File f : arquivos) {
                if (f.isFile()) qtd++;
            }
        }

        saida.writeInt(qtd);
        if (arquivos != null) {
            for (File f : arquivos) {
                if (f.isFile()) {
                    saida.writeUTF(f.getName());
                    saida.writeLong(f.length());
                }
            }
        }
        saida.flush();
    }

    /*
     * Envia um arquivo pro cliente (comando BAIXAR).
     *
     * O cliente manda o nome do arquivo que quer. Se o arquivo existir o
     * servidor responde "OK", manda o tamanho e depois os bytes.
     * Se nao existir responde "NAO_ENCONTRADO" e nao manda mais nada.
     */
    private void enviarArquivo() throws IOException {
        String nome = new File(entrada.readUTF()).getName();
        File arquivo = new File(ServidorSiCA.PASTA, nome);

        if (!arquivo.exists() || !arquivo.isFile()) {
            saida.writeUTF("NAO_ENCONTRADO");
            saida.flush();
            return;
        }

        saida.writeUTF("OK");
        saida.writeLong(arquivo.length());

        FileInputStream fis = new FileInputStream(arquivo);
        byte[] buffer = new byte[4096];
        int lidos;
        while ((lidos = fis.read(buffer)) != -1) {
            saida.write(buffer, 0, lidos);
        }
        fis.close();
        saida.flush();

        System.out.println("Arquivo enviado: " + nome);
    }
}
