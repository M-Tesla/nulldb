package client;

import network.OpCode;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Scanner;

public class NullDbClient {

    private static final int PORT = 5432;
    private static final String HOST = "127.0.0.1";
    private static final int PAYLOAD_MAX_SIZE = 48;
    private static final int PACKET_SIZE = 64;

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  NullDB Interactive CLI v1.0");
        System.out.println("  Type 'help' for commands or 'exit' to quit.");
        System.out.println("========================================");

        try (Scanner scanner = new Scanner(System.in);
             SocketChannel clientChannel = SocketChannel.open(new InetSocketAddress(HOST, PORT))) {

            while (true) {
                System.out.print("nulldb> ");
                String input = scanner.nextLine().trim();

                if (input.isEmpty()) continue;
                if (input.equalsIgnoreCase("exit")) break;
                if (input.equalsIgnoreCase("help")) {
                    printHelp();
                    continue;
                }

                String[] parts = input.split(" ", 3);
                String command = parts[0].toUpperCase();

                try {
                    long id = parts.length > 1 ? Long.parseLong(parts[1]) : 0;
                    String payload = parts.length == 3 ? parts[2] : "";

                    switch (command) {
                        case "INSERT":
                            dispatchCommand(clientChannel, OpCode.INSERT, id, payload);
                            break;
                        case "SELECT":
                            dispatchCommand(clientChannel, OpCode.SELECT, id, "");
                            break;
                        case "DELETE":
                            dispatchCommand(clientChannel, OpCode.DELETE, id, "");
                            break;
                        case "UPDATE":
                            dispatchCommand(clientChannel, OpCode.UPDATE, id, payload);
                            break;
                        case "SELECT_ALL":
                            dispatchCommand(clientChannel, OpCode.SELECT_ALL, 0L, "");
                            break;
                        default:
                            System.out.println("[ERROR] Invalid command syntax.");
                            printHelp();
                            break;
                    }
                } catch (NumberFormatException e) {
                    System.out.println("[ERROR] ID must be a valid number.");
                } catch (Exception e) {
                    System.out.println("[ERROR] Failed to execute command: " + e.getMessage());
                }
            }

            System.out.println("Connection closed. Goodbye.");

        } catch (Exception e) {
            System.err.println("[FATAL] Network error: " + e.getMessage());
        }
    }

    private static void dispatchCommand(
            SocketChannel channel,
            byte opCode,
            long id,
            String payload
    ) throws Exception {
        ByteBuffer buffer = ByteBuffer.allocateDirect(PACKET_SIZE);

        buffer.put(opCode);
        buffer.putLong(id);

        byte[] stringBytes = payload.getBytes();
        int lengthToCopy = Math.min(stringBytes.length, PAYLOAD_MAX_SIZE);
        buffer.put(stringBytes, 0, lengthToCopy);

        int padding = PAYLOAD_MAX_SIZE - lengthToCopy;
        for (int i = 0; i < padding; i++) {
            buffer.put((byte) 0x00);
        }

        buffer.flip();
        channel.write(buffer);
        readResponse(channel);
    }

    private static void readResponse(SocketChannel channel) throws Exception {
        ByteBuffer responseBuffer = ByteBuffer.allocateDirect(4096);
        int bytesRead = channel.read(responseBuffer);

        if (bytesRead > 0) {
            responseBuffer.flip();
            byte status = responseBuffer.get();
            byte[] msgBytes = new byte[responseBuffer.remaining()];
            responseBuffer.get(msgBytes);

            String statusStr = (status == 0x01) ? "SUCCESS" : "ERROR";
            System.out.println("[" + statusStr + "] " + new String(msgBytes).trim());
        } else {
            System.out.println("[ERROR] No response from server.");
        }
    }

    private static void printHelp() {
        System.out.println("\nAvailable commands:");
        System.out.println("  INSERT <id> <payload string>   (e.g., INSERT 2042 Null_Pointer)");
        System.out.println("  SELECT <id>                    (e.g., SELECT 2042)");
        System.out.println("  DELETE <id>                    (e.g., DELETE 2042)");
        System.out.println("  UPDATE <id> <new payload>      (e.g., UPDATE 2042 New_Data)");
        System.out.println("  SELECT_ALL                     (e.g., SELECT_ALL)");
        System.out.println("  exit                           (Close connection)");
    }
}