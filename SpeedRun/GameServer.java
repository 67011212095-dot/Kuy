import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public final class GameServer implements AutoCloseable {
    static final int MAX_PLAYERS = 3;
    private static final int MAX_PROGRESS = 100;

    private final Object stateLock = new Object();
    private final Player[] players = new Player[MAX_PLAYERS];
    private final ServerSocket serverSocket;
    private volatile boolean running = true;
    private int winnerLane = -1;

    public GameServer(int port) throws IOException {
        serverSocket = new ServerSocket(port);
        Thread acceptThread = new Thread(this::acceptConnections, "speedrun-server-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    public int getPort() {
        return serverSocket.getLocalPort();
    }

    private void acceptConnections() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                Thread clientThread = new Thread(() -> handleClient(socket), "speedrun-server-client");
                clientThread.setDaemon(true);
                clientThread.start();
            } catch (IOException exception) {
                if (running) {
                    System.err.println("Could not accept game client: " + exception.getMessage());
                }
            }
        }
    }

    private void handleClient(Socket socket) {
        Player player = null;
        try (Socket clientSocket = socket;
                BufferedReader input = new BufferedReader(
                        new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));
                PrintWriter output = new PrintWriter(
                        new OutputStreamWriter(clientSocket.getOutputStream(), StandardCharsets.UTF_8), true)) {
            clientSocket.setTcpNoDelay(true);
            String joinMessage = input.readLine();
            if (joinMessage == null || !joinMessage.startsWith("JOIN|")) {
                output.println("ERROR|ส่งข้อมูลเข้าร่วมไม่ถูกต้อง");
                return;
            }

            String name = decodeName(joinMessage.substring("JOIN|".length()));
            synchronized (stateLock) {
                int lane = findAvailableLane();
                if (lane < 0) {
                    output.println("ERROR|ห้องเต็มแล้ว (รองรับสูงสุด 3 คน)");
                    return;
                }
                player = new Player(lane, name, output);
                players[lane] = player;
                output.println("WELCOME|" + lane);
                broadcastState();
            }

            String message;
            while ((message = input.readLine()) != null && running) {
                synchronized (stateLock) {
                    if (players[player.lane] != player) {
                        break;
                    }
                    if ("STEP".equals(message)) {
                        advance(player);
                        broadcastState();
                    } else if ("RESET".equals(message)) {
                        resetRace();
                        broadcastState();
                    }
                }
            }
        } catch (IOException exception) {
            if (running) {
                System.err.println("Game client disconnected: " + exception.getMessage());
            }
        } finally {
            if (player != null) {
                synchronized (stateLock) {
                    if (players[player.lane] == player) {
                        players[player.lane] = null;
                        if (winnerLane == player.lane) {
                            winnerLane = -1;
                        }
                        broadcastState();
                    }
                }
            }
        }
    }

    private static String decodeName(String encoded) throws IOException {
        try {
            String name = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8).trim();
            if (name.isEmpty() || name.length() > 20 || name.indexOf('|') >= 0) {
                throw new IOException("ชื่อผู้เล่นต้องมี 1 ถึง 20 ตัวอักษร");
            }
            return name;
        } catch (IllegalArgumentException exception) {
            throw new IOException("ชื่อผู้เล่นไม่ถูกต้อง", exception);
        }
    }

    private int findAvailableLane() {
        for (int lane = 0; lane < players.length; lane++) {
            if (players[lane] == null) {
                return lane;
            }
        }
        return -1;
    }

    private void advance(Player player) {
        if (winnerLane >= 0 || player.progress >= MAX_PROGRESS) {
            return;
        }
        player.progress = Math.min(player.progress + 2, MAX_PROGRESS);
        if (player.progress == MAX_PROGRESS) {
            winnerLane = player.lane;
        }
    }

    private void resetRace() {
        winnerLane = -1;
        for (Player player : players) {
            if (player != null) {
                player.progress = 0;
            }
        }
    }

    private void broadcastState() {
        StringBuilder message = new StringBuilder("STATE|").append(winnerLane).append('|');
        boolean first = true;
        for (Player player : players) {
            if (player != null) {
                if (!first) {
                    message.append(';');
                }
                first = false;
                message.append(player.lane).append(',')
                        .append(encodeName(player.name)).append(',')
                        .append(player.progress);
            }
        }
        String state = message.toString();
        List<Player> failed = new ArrayList<>();
        for (Player player : players) {
            if (player != null && !player.send(state)) {
                failed.add(player);
            }
        }
        for (Player player : failed) {
            players[player.lane] = null;
        }
    }

    private static String encodeName(String name) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(name.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void close() {
        running = false;
        try {
            serverSocket.close();
        } catch (IOException exception) {
            System.err.println("Could not close game server: " + exception.getMessage());
        }
        synchronized (stateLock) {
            for (Player player : players) {
                if (player != null) {
                    player.close();
                }
            }
        }
    }

    private static final class Player {
        private final int lane;
        private final String name;
        private final PrintWriter output;
        private int progress;

        Player(int lane, String name, PrintWriter output) {
            this.lane = lane;
            this.name = name;
            this.output = output;
        }

        synchronized boolean send(String message) {
            output.println(message);
            return !output.checkError();
        }

        synchronized void close() {
            output.close();
        }
    }
}
