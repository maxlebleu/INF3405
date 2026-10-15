package client;

import java.util.Scanner;

public class Main {

    public static void main(String[] args) {

        Scanner scanner = new Scanner(System.in);

        String ip = askIp(scanner);
        int port = askPort(scanner);

        System.out.print("Nom d'utilisateur : ");
        String username = scanner.nextLine().trim();

        while (username.isEmpty()) {
            System.out.println("Le nom d'utilisateur ne peut pas être vide.");
            System.out.print("Nom d'utilisateur : ");
            username = scanner.nextLine().trim();
        }

        System.out.print("Mot de passe : ");
        String password = scanner.nextLine();

        while (password.isEmpty()) {
            System.out.println("Le mot de passe ne peut pas être vide.");
            System.out.print("Mot de passe : ");
            password = scanner.nextLine();
        }

        ChatClient client = new ChatClient(
                ip,
                port,
                username,
                password
        );

        client.start(scanner);

        scanner.close();
    }

    private static String askIp(Scanner scanner) {

        while (true) {

            System.out.print("Adresse IP du serveur : ");
            String ip = scanner.nextLine().trim();

            if (NetworkValidator.isValidIPv4(ip)) {
                return ip;
            }

            System.out.println("Adresse IP invalide.");
        }
    }

    private static int askPort(Scanner scanner) {

        while (true) {

            System.out.print("Port du serveur (5000-5050) : ");
            String input = scanner.nextLine().trim();

            try {

                int port = Integer.parseInt(input);

                if (NetworkValidator.isValidPort(port)) {
                    return port;
                }

            } catch (NumberFormatException e) {
                // Erreur affichée ci-dessous
            }

            System.out.println(
                    "Port invalide. Le port doit être compris entre 5000 et 5050."
            );
        }
    }
}
