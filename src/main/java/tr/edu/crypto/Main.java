package tr.edu.crypto;

import java.nio.file.Path;

public final class Main {
    public static void main(String[] args) {
        try {
            if (args.length < 2 || args.length > 3) {
                throw new IllegalArgumentException("Kullanım: server <web-port> <tcp-port> veya client <web-port>");
            }
            int webPort = parsePort(args[1]);
            if (args[0].equals("server") && args.length == 3) {
                RelayServer relay = new RelayServer(parsePort(args[2]));
                WebUi ui;
                try { ui = new WebUi(webPort, relay::state, null, relay.files()); }
                catch (Exception exception) { relay.close(); throw exception; }
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    ui.close();
                    try { relay.close(); } catch (Exception ignored) { /* Kapanis tamamlanir. */ }
                }));
                System.out.println("Sunucu: http://127.0.0.1:" + ui.port() + " | TCP: " + relay.port());
            } else if (args[0].equals("client") && args.length == 2) {
                ChatClient client = new ChatClient(Path.of("runtime", "client-" + webPort));
                WebUi ui = new WebUi(webPort, client::state, client);
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    ui.close();
                    try { client.close(); } catch (Exception ignored) { /* Kapanis tamamlanir. */ }
                }));
                System.out.println("İstemci: http://127.0.0.1:" + ui.port());
            } else throw new IllegalArgumentException("Geçersiz çalışma rolü veya parametre sayısı.");
        } catch (Exception exception) {
            System.err.println("Başlatılamadı: " + exception.getMessage());
            System.exit(1);
        }
    }

    private static int parsePort(String value) {
        int port = Integer.parseInt(value);
        if (port < 1 || port > 65535) throw new IllegalArgumentException("Port 1–65535 arasında olmalı.");
        return port;
    }
}
