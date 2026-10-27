import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.event.ActionEvent;

public class Game extends JFrame implements GameClient.Listener {
    private static final long serialVersionUID = 1L;

    private static final String UI_FONT = "Leelawadee UI";
    private static final Color BACKGROUND = new Color(242, 246, 250);
    private static final Color[] LANE_COLORS = {
            new Color(233, 245, 255),
            new Color(238, 249, 239),
            new Color(255, 246, 232)
    };

    private final List<TrackPanel> tracks = new ArrayList<>();
    private final JLabel messageLabel = new JLabel("คลิกเลือกเลน แล้วกด Space และปล่อยเพื่อวิ่ง", SwingConstants.CENTER);
    private final GameClient client;
    private final String serverAddress;
    private final int serverPort;
    private boolean raceOver;
    private boolean spacePressed;
    private int selectedTrack;
    private int onlineLane = -1;

    public Game() {
        this(null, null, -1);
    }

    public Game(GameClient client) {
        this(client, null, -1);
    }

    public Game(GameClient client, String serverAddress, int serverPort) {
        this.client = client;
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
        setTitle("Speed Run");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(760, 520));
        setSize(1280, 800);
        setLocationRelativeTo(null);

        BufferedImage runnerImage = loadRunnerImage();
        setLayout(new BorderLayout(12, 12));
        getContentPane().setBackground(BACKGROUND);

        JLabel title = new JLabel("SPEED RUN", SwingConstants.CENTER);
        title.setFont(new Font(UI_FONT, Font.BOLD, 28));
        title.setBorder(BorderFactory.createEmptyBorder(18, 12, 0, 12));
        add(title, BorderLayout.NORTH);

        JPanel trackBoard = new JPanel(new GridLayout(3, 1, 0, 8));
        trackBoard.setBackground(BACKGROUND);
        trackBoard.setBorder(BorderFactory.createEmptyBorder(4, 18, 4, 18));
        for (int i = 0; i < 3; i++) {
            TrackPanel track = new TrackPanel(i + 1, LANE_COLORS[i], runnerImage);
            final int trackIndex = i;
            track.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mousePressed(java.awt.event.MouseEvent event) {
                    if (client == null && SwingUtilities.isLeftMouseButton(event)) {
                        selectTrack(trackIndex);
                    }
                }
            });
            tracks.add(track);
            trackBoard.add(track);
        }
        add(trackBoard, BorderLayout.CENTER);

        JButton resetButton = new JButton("เริ่มใหม่");
        resetButton.setFont(new Font(UI_FONT, Font.PLAIN, 14));
        resetButton.setFocusable(false);
        resetButton.addActionListener(event -> resetRace());
        JButton backButton = new JButton("กลับหน้าแรก");
        backButton.setFont(new Font(UI_FONT, Font.PLAIN, 14));
        backButton.setFocusable(false);
        backButton.addActionListener(event -> dispose());
        messageLabel.setFont(new Font(UI_FONT, Font.PLAIN, 15));

        JPanel footer = new JPanel(new BorderLayout(12, 0));
        footer.setBackground(BACKGROUND);
        footer.setBorder(BorderFactory.createEmptyBorder(0, 18, 18, 18));
        footer.add(backButton, BorderLayout.WEST);
        footer.add(messageLabel, BorderLayout.CENTER);
        footer.add(resetButton, BorderLayout.EAST);
        if (client == null) {
            add(footer, BorderLayout.SOUTH);
        } else {
            JPanel bottomPanel = new JPanel(new BorderLayout(0, 6));
            bottomPanel.setBackground(BACKGROUND);
            bottomPanel.add(footer, BorderLayout.CENTER);

            JLabel serverInfo = new JLabel(
                    "Server: " + serverAddress + "    Port: " + serverPort,
                    SwingConstants.CENTER);
            serverInfo.setFont(new Font(UI_FONT, Font.PLAIN, 13));
            serverInfo.setForeground(new Color(70, 82, 94));
            serverInfo.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(205, 215, 225)),
                    BorderFactory.createEmptyBorder(8, 12, 12, 12)));
            bottomPanel.add(serverInfo, BorderLayout.SOUTH);
            add(bottomPanel, BorderLayout.SOUTH);
        }

        selectTrack(0);
        installSpaceKeyBinding();
        if (client != null) {
            messageLabel.setText("เชื่อมต่อแล้ว กำลังรับข้อมูลผู้เล่น...");
            client.start(this);
            addWindowListener(new java.awt.event.WindowAdapter() {
                @Override
                public void windowClosed(java.awt.event.WindowEvent event) {
                    client.close();
                }
            });
        }
    }

    private static BufferedImage loadRunnerImage() {
        File imageFile = new File("images", "Run.png");
        try {
            BufferedImage image = ImageIO.read(imageFile);
            if (image == null) {
                throw new IOException("The file is not a supported image.");
            }
            return image;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Could not load " + imageFile.getPath() + ". Run the game from the project folder.",
                    exception);
        }
    }

    private void installSpaceKeyBinding() {
        JComponent rootPane = getRootPane();
        rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke("pressed SPACE"), "spacePressed");
        rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke("released SPACE"), "spaceReleased");
        rootPane.getActionMap().put("spacePressed", new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent event) {
                spacePressed = true;
            }
        });
        rootPane.getActionMap().put("spaceReleased", new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent event) {
                if (spacePressed) {
                    spacePressed = false;
                    runSelectedTrack();
                }
            }
        });
    }

    private void selectTrack(int index) {
        selectedTrack = index;
        for (int i = 0; i < tracks.size(); i++) {
            tracks.get(i).setSelected(i == index);
        }
        if (!raceOver) {
            messageLabel.setText("เลือกเลน " + (index + 1) + " แล้วกด Space และปล่อยเพื่อวิ่ง");
        }
    }

    private void runSelectedTrack() {
        if (raceOver) {
            return;
        }
        if (client != null) {
            if (onlineLane >= 0) {
                client.sendStep();
            }
            return;
        }
        if (tracks.get(selectedTrack).runStep()) {
            raceOver = true;
            messageLabel.setText("เลน " + (selectedTrack + 1) + " เข้าเส้นชัยแล้ว! คลิกเริ่มใหม่เพื่อแข่งอีกครั้ง");
        } else {
            messageLabel.setText("ปล่อยปุ่ม Space แล้ว กดและปล่อยอีกครั้งเพื่อวิ่งต่อ");
        }
        repaint();
    }

    private void resetRace() {
        if (client != null) {
            client.resetRace();
            return;
        }
        for (TrackPanel track : tracks) {
            track.reset();
        }
        raceOver = false;
        spacePressed = false;
        selectTrack(0);
    }

    @Override
    public void onConnected(int lane) {
        SwingUtilities.invokeLater(() -> {
            if (lane < 0 || lane >= tracks.size()) {
                messageLabel.setText("Server ส่งหมายเลขเลนไม่ถูกต้อง");
                return;
            }
            onlineLane = lane;
            selectedTrack = lane;
            for (int i = 0; i < tracks.size(); i++) {
                tracks.get(i).setSelected(i == lane);
            }
            messageLabel.setText("คุณอยู่เลน " + (lane + 1) + " — กด Space แล้วปล่อยเพื่อวิ่ง");
        });
    }

    @Override
    public void onState(int winnerLane, GameClient.PlayerState[] players) {
        SwingUtilities.invokeLater(() -> {
            for (int i = 0; i < tracks.size(); i++) {
                GameClient.PlayerState player = players[i];
                tracks.get(i).setOnlineState(
                        player == null ? "รอผู้เล่น..." : player.name,
                        player == null ? 0 : player.progress,
                        player != null);
            }
            raceOver = winnerLane >= 0;
            if (winnerLane >= 0) {
                messageLabel.setText("ผู้เล่นเลน " + (winnerLane + 1) + " เข้าเส้นชัยแล้ว! กดเริ่มใหม่เพื่อแข่งอีกครั้ง");
            } else if (onlineLane >= 0) {
                messageLabel.setText("คุณอยู่เลน " + (onlineLane + 1) + " — กด Space แล้วปล่อยเพื่อวิ่ง");
            }
        });
    }

    @Override
    public void onError(String message) {
        SwingUtilities.invokeLater(() -> messageLabel.setText(message));
    }

    @Override
    public void onDisconnected() {
        SwingUtilities.invokeLater(() -> messageLabel.setText("หลุดจาก Server แล้ว"));
    }

    private static class TrackPanel extends JPanel {
        private static final long serialVersionUID = 1L;
        private static final int RUNNER_SIZE = 70;
        private static final int START_X = 24;
        private static final int RUNNER_STEP = 12;

        private final int laneNumber;
        private final BufferedImage runnerImage;
        private String playerName;
        private int progress;
        private boolean selected;
        private boolean playerActive = true;

        TrackPanel(int laneNumber, Color laneColor, BufferedImage runnerImage) {
            this.laneNumber = laneNumber;
            this.runnerImage = runnerImage;
            playerName = "ผู้เล่น " + laneNumber;
            setOpaque(true);
            setBackground(laneColor);
            setPreferredSize(new Dimension(700, 150));
            setBorder(BorderFactory.createLineBorder(new Color(195, 205, 215), 1));
        }

        void setSelected(boolean selected) {
            this.selected = selected;
            repaint();
        }

        boolean runStep() {
            progress = Math.min(progress + RUNNER_STEP, getFinishProgress());
            repaint();
            return progress >= getFinishProgress();
        }

        void setOnlineState(String name, int progressPercent, boolean active) {
            playerActive = active;
            playerName = active ? name : "รอผู้เล่น...";
            progress = Math.round(getFinishProgress() * progressPercent / 100.0f);
            repaint();
        }

        void reset() {
            progress = 0;
            repaint();
        }

        private int getFinishProgress() {
            return Math.max(0, getWidth() - RUNNER_SIZE - START_X - 28);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            if (selected) {
                g.setColor(new Color(55, 125, 190));
                g.setStroke(new BasicStroke(3));
                g.drawRect(2, 2, getWidth() - 5, getHeight() - 5);
            }

            g.setColor(new Color(82, 91, 101));
            g.setFont(new Font(UI_FONT, Font.BOLD, 14));
            g.drawString(playerName + (selected ? "  (คุณ)" : ""), 14, 23);

            int finishX = getWidth() - 28;
            g.setColor(new Color(90, 100, 110));
            g.setStroke(new BasicStroke(2));
            g.drawLine(START_X, getHeight() - 18, finishX, getHeight() - 18);

            g.setColor(new Color(215, 72, 72));
            g.setStroke(new BasicStroke(3));
            g.drawLine(finishX, 10, finishX, getHeight() - 14);
            g.setFont(new Font(UI_FONT, Font.PLAIN, 12));
            g.drawString("FINISH", Math.max(4, finishX - 45), 24);

            int runnerY = Math.max(30, (getHeight() - RUNNER_SIZE) / 2);
            if (playerActive) {
                g.drawImage(runnerImage, START_X + progress, runnerY, RUNNER_SIZE, RUNNER_SIZE, null);
            }
            g.dispose();
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new Game().setVisible(true));
    }
}
