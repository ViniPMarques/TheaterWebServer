import java.io.*;
import java.net.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

public class TheaterWebServer {
    private static final int PORT = 8080;
    private static final String INDEX_PAGE = "index.html";
    private static final String LOG_FILE = "log.txt";

    // Mapa que armazena os assentos e os respectivos nomes dos reservantes
    private static Map<String, String> theaterMap = new ConcurrentHashMap<>();

    // Fila de registro (log) para armazenar as informacoes das reservas
    private static BlockingQueue<String> logQueue = new LinkedBlockingQueue<>();

    // Objeto de bloqueio para garantir a exclusao mutua ao acessar o mapa de assentos
    private static Object lock = new Object();

    // Lista de assentos disponiveis
    private static List<String> seats = Arrays.asList("A1", "A2", "A3", "A4", "A5", "B1", "B2", "B3", "B4", "B5",
            "C1", "C2", "C3", "C4", "C5", "D1", "D2", "D3", "D4", "D5");

    public static void main(String[] args) {
        try {
            ServerSocket serverSocket = new ServerSocket(PORT);
            System.out.println("Servidor Web iniciado na porta " + PORT);

            // Inicia a thread de registro (logger) para escrever as informacoes das reservas em um arquivo de log
            LoggerThread loggerThread = new LoggerThread();
            loggerThread.start();

            while (true) {
                // Aguarda e aceita a conexao do cliente
                Socket clientSocket = serverSocket.accept();

                // Inicia uma nova thread para tratar a requisicao do cliente
                Thread requestThread = new Thread(new RequestHandler(clientSocket));
                requestThread.start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Classe para tratar as requisicoes do cliente
    private static class RequestHandler implements Runnable {
        private Socket clientSocket;

        public RequestHandler(Socket clientSocket) {
            this.clientSocket = clientSocket;
        }

        @Override
        public void run() {
            try {
                // Cria leitor de entrada e escritor de saida para comunicacao com o cliente
                BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                OutputStreamWriter writer = new OutputStreamWriter(clientSocket.getOutputStream());

                // Le a requisicao do cliente
                String request = reader.readLine();
                System.out.println("Requisição recebida: " + request);

                // Divide a requisicao em partes separadas por espaço
                String[] requestParts = request.split(" ");
                String method = requestParts[0];
                String path = requestParts[1];

                // Trata as requisicoes GET
                if (method.equals("GET")) {
                    if (path.equals("/")) {
                        // Para a pagina inicial
                        serveIndexPage(writer);
                    } else if (path.startsWith("/reserve")) {
                        // Processa a reserva
                        String response;
                        synchronized (lock) {
                            response = processReservation(path);
                        }
                        writer.write(response);
                    } else if (path.equals("/success")) {
                        // Para a pagina de sucesso
                        serveSuccessPage(writer);
                    } else {
                        // Pagina nao encontrada
                        writer.write("HTTP/1.1 404 Not Found\r\n");
                    }
                } else {
                    // Metodo nao permitido
                    writer.write("HTTP/1.1 405 Method Not Allowed\r\n");
                }

                // Limpa o buffer e fecha os fluxos de entrada e saida
                writer.flush();
                writer.close();
                reader.close();
                clientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // Pagina inicial
        private void serveIndexPage(OutputStreamWriter writer) throws IOException {
            synchronized (lock) {
                writer.write("HTTP/1.1 200 OK\r\n");
                writer.write("Content-Type: text/html\r\n");
                writer.write("\r\n");

                writer.write("<html><head><title>Reserva de Assentos</title>");
                // Estilos CSS embutidos na pagina
                writer.write("<style>");
                writer.write("");
                writer.write("body { font-family: Arial, sans-serif; width: 650px; margin-left: auto; margin-right: auto; \n}");
                writer.write("input[type=text] { width: 550px; padding: 12px 18px; display: inline-block; border: 1px solid #ccc; border-radius: 4px; box-sizing: border-box; background-color: #d1d1d1;}");
                writer.write("input[type=submit] { display: inline-block; border-radius: 25px; width: 80px; text-align: center; margin: 5px; padding: 5px; background-color: #3d8a59; color: #fff;}");
                writer.write("h1 { color: #333333; text-align: center;}");
                writer.write("h2 { color: #555555; text-align: center;}");
                writer.write("form { margin-top: 10px; }");
                writer.write(".seat { display: inline-block; border-radius: 25px; width: 100px; text-align: center; margin: 5px; padding: 5px; background-color: #99CC99; }");
                writer.write(".reserved { background-color: #a01137; color: #ffffff;}");
                writer.write("a:link { text-decoration: none; }");
                writer.write("a:visited { text-decoration: none; }");
                writer.write("a:hover { text-decoration: underline; }");
                writer.write("a:active { text-decoration: underline; }");
                writer.write("</style>");
                writer.write("</head><body>\r\n");
                writer.write("<h1>Reserva de Assentos</h1>\r\n");
                writer.write("<form action=\"/reserve\" method=\"GET\" onsubmit=\"return validateName()\">"); // Adiciona um evento onsubmit para chamar a funcao de validacao
                writer.write("Nome: <input type=\"text\" id=\"name\" name=\"name\"><br><br>"); // Adiciona um id ao campo de input do nome
                writer.write("<h2>Status dos Assentos:</h2>\r\n");
                for (String seat : seats) {
                    String reservedBy = theaterMap.get(seat);
                    writer.write("<div class=\"seat");
                    if (reservedBy != null) {
                        writer.write(" reserved");
                    }
                    writer.write("\">");
                    writer.write(seat);
                    writer.write("<br>");
                    if (reservedBy != null) {
                        writer.write("Reservado por: " + reservedBy);
                    } else {
                        writer.write("<input type=\"submit\" name=\"seat\" value=\"" + seat + "\">");
                    }
                    writer.write("</div>\r\n");
                }
                writer.write("</form>");
                writer.write("<script>");
                writer.write("function validateName() {"); // Funcao de validacao do nome
                writer.write("  var nameInput = document.getElementById('name');"); // Obtem o elemento de input do nome
                writer.write("  var name = nameInput.value.trim();"); // Obtem o valor do nome e remove espaços em branco no inicio e no fim
                writer.write("  if (!name.match(/^[a-zA-Z]+$/)) {"); // Verifica se o nome contem apenas letras
                writer.write("    alert('O nome inserido eh invalido, por favor tente novamente.');"); // Exibe um alerta com a mensagem de erro
                writer.write("    return false;"); // Impede o envio do formulario
                writer.write("  }");
                writer.write("  return true;"); // Permite o envio do formulario se o nome for valido
                writer.write("}");
                writer.write("</script>");
                writer.write("</body></html>\r\n");
            }
        }

        // Processa a reserva do assento
        private String processReservation(String path) {
            String[] pathParts = path.split("\\?");

            String[] paramParts = pathParts[1].split("&");
            String name = null;
            String seat = null;

            for (String param : paramParts) {
                String[] keyValue = param.split("=");
                if (keyValue.length == 2) {
                    String key = keyValue[0];
                    String value = keyValue[1];
                    if (key.equals("name")) {
                        name = value;
                    } else if (key.equals("seat")) {
                        seat = value;
                    }
                }
            }

            if (name != null && isNameValid(name)) {
                synchronized (lock) {
                    theaterMap.put(seat, name);
                    LocalDateTime now = LocalDateTime.now();
                    String reservationDateTime = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                    theaterMap.put(seat, name + " (Reservado em: " + reservationDateTime + ")");
                    String logEntry = "IP: " + clientSocket.getInetAddress().getHostAddress() + ", Assento: " + seat + ", Nome: " + name + ", Data e Hora da Reserva: " + reservationDateTime;
                    logQueue.offer(logEntry);
                    // Redireciona para a pagina de sucesso
                    return "HTTP/1.1 302 Found\r\nLocation: /success\r\n\r\n";
                }
            }
            return name;
        }

        // Pagina de sucesso
        private void serveSuccessPage(OutputStreamWriter writer) throws IOException {
            synchronized (lock) {
                writer.write("HTTP/1.1 200 OK\r\n");
                writer.write("Content-Type: text/html\r\n");
                writer.write("\r\n");

                writer.write("<html><head><title>Reserva de Assentos</title>");
                // Estilos CSS embutidos na pagina
                writer.write("<style>");
                writer.write("body { font-family: Arial, sans-serif; width: 650px; margin-left: auto; margin-right: auto; }");
                writer.write("h1 { color: #333333; text-align: center; }");
                writer.write("p { text-align: center; }");
                writer.write("a { text-decoration: none; display: block; text-align: center; margin-top: 20px; }");
                writer.write("</style>");
                writer.write("</head><body>\r\n");
                writer.write("<h1>Sucesso</h1>\r\n");
                writer.write("<p>Sua reserva foi efetuada com sucesso.</p>\r\n");
                writer.write("<a href=\"/\">Voltar para a Pagina Inicial</a>\r\n");
                writer.write("</body></html>\r\n");
            }
        }

        // Verifica se o nome inserido pelo usuário eh valido
        private boolean isNameValid(String name) {
            return name.matches("[a-zA-Z]+");
        }
    }

    // Thread para escrever as informacoes das reservas em um arquivo de log
    private static class LoggerThread extends Thread {
        @Override
        public void run() {
            try (FileWriter fileWriter = new FileWriter(LOG_FILE, true);
                 BufferedWriter bufferedWriter = new BufferedWriter(fileWriter)) {

                while (true) {
                    String logEntry = logQueue.take();
                    bufferedWriter.write(logEntry);
                    bufferedWriter.newLine();
                    bufferedWriter.flush();
                }
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
