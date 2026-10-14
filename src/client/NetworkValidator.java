package client;

public final class NetworkValidator {

    private NetworkValidator() {
    }

    public static boolean isValidIPv4(String ip) {

        if (ip == null || ip.isBlank()) {
            return false;
        }

        String[] parts = ip.trim().split("\\.", -1);

        if (parts.length != 4) {
            return false;
        }

        for (String part : parts) {

            if (part.isEmpty()) {
                return false;
            }

            try {

                int value = Integer.parseInt(part);

                if (value < 0 || value > 255) {
                    return false;
                }

            } catch (NumberFormatException e) {

                return false;
            }
        }

        return true;
    }

    public static boolean isValidPort(int port) {

        return port >= 5000 && port <= 5050;
    }
}
