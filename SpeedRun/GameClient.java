import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class GameClient implements AutoCloseable {
    public interface Listener {
        void onConnected(int lane);

        void onState(int winnerLane, PlayerState[] players);

        void onError(String message);

        void onDisconnected();
    }

    public static final class PlayerState {
        public final int lane;
        public final String name;
        public final int progress;

        PlayerState(int lane, String name, int progress) {
            this.lane = lane;
            this.name = name;
            this.progress = progress;
        }
    }

    private final Socket socket;
    private final PrintWriter output;
    private volatile Listener listener;
    private volatile boolean closed;

    public GameClient(String host, int port, String playerName) throws IOException {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), 5000);
        socket.setTcpNoDelay(true);
        output = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
        String encodedName = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(playerName.trim().getBytes(StandardCharsets.UTF_8));
        output.println("JOIN|" + encodedName);
    }

    public void start(Listener listener) {
        this.listener = listener;
        Thread readerThread = new Thread(this::readMessages, "speedrun-client-reader");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    public void sendStep() {
        send("STEP");
    }

    public void resetRace() {
        send("RESET");
    }

    private synchronized void send(String message) {
        if (!closed) {
            output.println(message);
            if (output.checkError()) {
                close();
                notifyError("ส่งข้อมูลไปยัง Server ไม่สำเร็จ");
            }
        }
    }

    private void readMessages() {
        try (BufferedReader input = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
            String message;
            while (!closed && (message = input.readLine()) != null) {
                handleMessage(message);
            }
        } catch (IOException exception) {
            if (!closed) {
                notifyError("การเชื่อมต่อขาดหาย: " + exception.getMessage());
            }
        } finally {
            boolean notify = !closed;
            close();
            if (notify) {
                Listener currentListener = listener;
                if (currentListener != null) {
                    currentListener.onDisconnected();
                }
            }
        }
    }

    private void handleMessage(String message) {
        Listener currentListener = listener;
        if (currentListener == null) {
            return;
        }
        if (message.startsWith("WELCOME|")) {
            try {
                currentListener.onConnected(Integer.parseInt(message.substring("WELCOME|".length())));
            } catch (NumberFormatException exception) {
                notifyError("ข้อมูลเลนจาก Server ไม่ถูกต้อง");
            }
        } else if (message.startsWith("ERROR|")) {
            currentListener.onError(message.substring("ERROR|".length()));
            close();
        } else if (message.startsWith("STATE|")) {
            parseState(message);
        }
    }

    private void parseState(String message) {
        String[] parts = message.split("\\|", 3);
        if (parts.length != 3) {
            notifyError("ข้อมูลสถานะเกมจาก Server ไม่ถูกต้อง");
            return;
        }
        try {
            int winnerLane = Integer.parseInt(parts[1]);
            PlayerState[] players = new PlayerState[GameServer.MAX_PLAYERS];
            if (!parts[2].isEmpty()) {
                for (String playerInfo : parts[2].split(";")) {
                    String[] values = playerInfo.split(",", 3);
                    if (values.length != 3) {
                        throw new IllegalArgumentException("Invalid player state");
                    }
                    int lane = Integer.parseInt(values[0]);
                    int progress = Integer.parseInt(values[2]);
                    if (lane < 0 || lane >= players.length || progress < 0 || progress > 100) {
                        throw new IllegalArgumentException("Player state is out of range");
                    }
                    String name = new String(Base64.getUrlDecoder().decode(values[1]), StandardCharsets.UTF_8);
                    players[lane] = new PlayerState(lane, name, progress);
                }
            }
            Listener currentListener = listener;
            if (currentListener != null) {
                currentListener.onState(winnerLane, players);
            }
        } catch (IllegalArgumentException exception) {
            notifyError("ไม่สามารถอ่านสถานะเกมจาก Server ได้");
        }
    }

    private void notifyError(String message) {
        Listener currentListener = listener;
        if (currentListener != null) {
            currentListener.onError(message);
        }
    }

    @Override
    public synchronized void close() {
        if (!closed) {
            closed = true;
            try {
                output.close();
                socket.close();
            } catch (IOException exception) {
                System.err.println("Could not close game client: " + exception.getMessage());
            }
        }
    }
}
