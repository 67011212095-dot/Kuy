import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javax.imageio.ImageIO;
import javax.swing.*;

public class Game extends JFrame implements GameClient.Listener {
    private static final long serialVersionUID = 1L;

    private static final String UI_FONT = "Leelawadee UI";
    private static final Color BACKGROUND = new Color(24, 30, 38);

    // สีพื้นลู่วิ่งดินเผาแบบสปอร์ต (Tartan Track) ของแต่ละเลน
    private static final Color[] LANE_COLORS = {
            new Color(186, 68, 54),
            new Color(174, 58, 46),
            new Color(192, 74, 58)
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

    // ระบบเอฟเฟกต์ฉลองชัยชนะ
    private boolean celebrationStarted = false;
    private final CelebrationOverlay celebrationOverlay;

    public Game() {
        this(null, null, -1);
    }

    public Game(GameClient client) {
        this(client, null, -1);
    }

    // ตั้งค่าหน้าต่างเกมและสร้าง UI ทั้งหมด
    public Game(GameClient client, String serverAddress, int serverPort) {
        this.client = client;
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
        setTitle("Speed Run");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(860, 560));
        setSize(1280, 800);
        setLocationRelativeTo(null);

        BufferedImage runnerImage = loadRunnerImage();
        setLayout(new BorderLayout(12, 12));
        getContentPane().setBackground(BACKGROUND);

        // หัวข้อด้านบน
        JLabel title = new JLabel("SPEED RUN", SwingConstants.CENTER);
        title.setFont(new Font(UI_FONT, Font.BOLD, 28));
        title.setForeground(new Color(255, 215, 60));
        title.setBorder(BorderFactory.createEmptyBorder(18, 12, 4, 12));
        add(title, BorderLayout.NORTH);

        // แผงสนามแข่ง 3 เลน ล้อมรอบด้วยแถบหญ้าสีเขียว
        JPanel trackBoardContainer = new JPanel(new BorderLayout());
        trackBoardContainer.setBackground(BACKGROUND);
        trackBoardContainer.setBorder(BorderFactory.createEmptyBorder(6, 18, 6, 18));

        JPanel topGrass = createGrassStrip();
        JPanel bottomGrass = createGrassStrip();
        trackBoardContainer.add(topGrass, BorderLayout.NORTH);
        trackBoardContainer.add(bottomGrass, BorderLayout.SOUTH);

        JPanel trackBoard = new JPanel(new GridLayout(3, 1, 0, 0));
        trackBoard.setBackground(BACKGROUND);
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
        trackBoardContainer.add(trackBoard, BorderLayout.CENTER);
        add(trackBoardContainer, BorderLayout.CENTER);

        // ปุ่มควบคุมด้านล่าง (เริ่มใหม่ / กลับหน้าแรก / ข้อความสถานะ)
        JButton resetButton = new JButton("เริ่มใหม่");
        resetButton.setFont(new Font(UI_FONT, Font.BOLD, 14));
        resetButton.setFocusable(false);
        resetButton.setForeground(Color.WHITE);
        resetButton.setBackground(new Color(45, 125, 75));
        resetButton.setBorder(BorderFactory.createEmptyBorder(8, 18, 8, 18));
        resetButton.addActionListener(event -> resetRace());

        JButton backButton = new JButton("กลับหน้าแรก");
        backButton.setFont(new Font(UI_FONT, Font.PLAIN, 14));
        backButton.setFocusable(false);
        backButton.setForeground(new Color(220, 230, 240));
        backButton.setBackground(new Color(55, 68, 85));
        backButton.setBorder(BorderFactory.createEmptyBorder(8, 18, 8, 18));
        backButton.addActionListener(event -> dispose());

        messageLabel.setFont(new Font(UI_FONT, Font.PLAIN, 15));
        messageLabel.setForeground(new Color(230, 238, 248));

        JPanel footer = new JPanel(new BorderLayout(12, 0));
        footer.setBackground(BACKGROUND);
        footer.setBorder(BorderFactory.createEmptyBorder(0, 18, 18, 18));
        footer.add(backButton, BorderLayout.WEST);
        footer.add(messageLabel, BorderLayout.CENTER);
        footer.add(resetButton, BorderLayout.EAST);

        if (client == null) {
            add(footer, BorderLayout.SOUTH);
        } else {
            // แถบข้อมูล Server เพิ่มเติมสำหรับโหมดออนไลน์
            JPanel bottomPanel = new JPanel(new BorderLayout(0, 6));
            bottomPanel.setBackground(BACKGROUND);
            bottomPanel.add(footer, BorderLayout.CENTER);

            JLabel serverInfo = new JLabel(
                    "Server: " + serverAddress + "    Port: " + serverPort,
                    SwingConstants.CENTER);
            serverInfo.setFont(new Font(UI_FONT, Font.PLAIN, 13));
            serverInfo.setForeground(new Color(155, 175, 195));
            serverInfo.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(50, 65, 80)),
                    BorderFactory.createEmptyBorder(8, 12, 12, 12)));
            bottomPanel.add(serverInfo, BorderLayout.SOUTH);
            add(bottomPanel, BorderLayout.SOUTH);
        }

        // ติดตั้ง GlassPane สำหรับเอฟเฟกต์ Confetti และกล่องประกาศผู้ชนะ
        celebrationOverlay = new CelebrationOverlay(this::resetRace);
        setGlassPane(celebrationOverlay);

        selectTrack(0);
        installSpaceKeyBinding();

        // เริ่มต้นการเชื่อมต่อถ้าเป็นโหมดออนไลน์
        if (client != null) {
            messageLabel.setText("เชื่อมต่อแล้ว กำลังรับข้อมูลผู้เล่น...");
            client.start(this);
            addWindowListener(new java.awt.event.WindowAdapter() {
                @Override
                public void windowClosed(java.awt.event.WindowEvent event) {
                    celebrationOverlay.stopCelebration();
                    client.close();
                }
            });
        }
    }

    // แถบขอบหญ้าประดับบน-ล่างของลู่วิ่ง
    private static JPanel createGrassStrip() {
        JPanel grass = new JPanel();
        grass.setPreferredSize(new Dimension(100, 10));
        grass.setBackground(new Color(46, 125, 50));
        grass.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, new Color(30, 85, 35)));
        return grass;
    }

    // โหลดรูปภาพสไปรต์ตัวละครนักวิ่ง
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

    // ดักจับปุ่ม Spacebar (กดแล้วปล่อย = วิ่ง 1 ก้าว)
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

    // เลือกเลนที่ต้องการควบคุม
    private void selectTrack(int index) {
        selectedTrack = index;
        for (int i = 0; i < tracks.size(); i++) {
            tracks.get(i).setSelected(i == index);
        }
        if (!raceOver) {
            messageLabel.setText("เลือกเลน " + (index + 1) + " แล้วกด Space และปล่อยเพื่อวิ่ง");
        }
    }

    // คำนวณการวิ่งไปข้างหน้าเมื่อกดปล่อย Spacebar
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
            String winnerName = tracks.get(selectedTrack).getPlayerName();
            messageLabel.setText(String.format("เลน %d เข้าเส้นชัยแล้ว! เป็นผู้ชนะการแข่งขัน", selectedTrack + 1));
            startCelebration(winnerName, selectedTrack + 1);
        } else {
            messageLabel.setText("ปล่อยปุ่ม Space แล้ว กดและปล่อยอีกครั้งเพื่อวิ่งต่อ");
        }
        repaint();
    }

    // รีเซ็ตการแข่งขันเพื่อเริ่มรอบใหม่
    private void resetRace() {
        celebrationStarted = false;
        celebrationOverlay.stopCelebration();
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
        repaint();
    }

    // เปิดเอฟเฟกต์ฉลองชัยชนะ (Confetti + ประกายแสง)
    private void startCelebration(String winnerName, int winnerLane) {
        if (celebrationStarted) {
            return;
        }
        celebrationStarted = true;
        celebrationOverlay.startCelebration(winnerName, winnerLane);
    }

    // --- ส่วนรับอีเวนต์จาก GameClient (โหมดออนไลน์) ---

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
                String winnerName = (winnerLane >= 0 && winnerLane < players.length && players[winnerLane] != null)
                        ? players[winnerLane].name
                        : "ผู้เล่น " + (winnerLane + 1);
                messageLabel.setText(String.format("ผู้เล่นเลน %d เข้าเส้นชัยแล้ว! เป็นผู้ชนะการแข่งขัน",
                        winnerLane + 1));
                startCelebration(winnerName, winnerLane + 1);
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
        SwingUtilities.invokeLater(() -> {
            celebrationOverlay.stopCelebration();
            messageLabel.setText("หลุดจาก Server แล้ว");
        });
    }

    // =========================================================================
    // คลาสย่อย: CelebrationOverlay (หน้ากากวาด Confetti, ประกายดาว และกล่องผู้ชนะ)
    // =========================================================================
    private static class CelebrationOverlay extends JComponent {
        private static final long serialVersionUID = 1L;
        private final List<ConfettiParticle> confettis = new ArrayList<>();
        private final List<SparkleParticle> sparkles = new ArrayList<>();
        private final Timer animTimer;
        private final JButton playAgainButton;

        private boolean active = false;
        private String winnerName = "";
        private int winnerLane = -1;
        private final Random random = new Random();

        CelebrationOverlay(Runnable onReset) {
            setLayout(null);
            setOpaque(false);

            // ปุ่มกดเริ่มแข่งใหม่บนการ์ดประกาศผล
            playAgainButton = new JButton("แข่งอีกครั้ง (เริ่มใหม่)");
            playAgainButton.setFont(new Font(UI_FONT, Font.BOLD, 15));
            playAgainButton.setForeground(Color.WHITE);
            playAgainButton.setBackground(new Color(40, 150, 85));
            playAgainButton.setFocusPainted(false);
            playAgainButton.setBorder(BorderFactory.createEmptyBorder(10, 24, 10, 24));
            playAgainButton.addActionListener(e -> {
                stopCelebration();
                if (onReset != null) {
                    onReset.run();
                }
            });
            add(playAgainButton);

            // ตัวจับเวลาแอนิเมชันสำหรับอัปเดตฟิสิกส์ 50 FPS
            animTimer = new Timer(20, e -> {
                if (active) {
                    updatePhysics();
                    repaint();
                }
            });
        }

        // เริ่มต้นสร้างเศษกระดาษและประกายแสงเมื่อมีผู้ชนะ
        void startCelebration(String winnerName, int winnerLane) {
            this.winnerName = winnerName;
            this.winnerLane = winnerLane;
            this.active = true;

            int w = Math.max(800, getWidth());
            int h = Math.max(600, getHeight());

            // สุ่มสร้างเศษกระดาษหลากสี 110 ชิ้น
            confettis.clear();
            Color[] colors = {
                    new Color(255, 65, 85),
                    new Color(255, 215, 55),
                    new Color(60, 205, 255),
                    new Color(110, 235, 110),
                    new Color(255, 140, 50),
                    new Color(215, 115, 255),
                    new Color(255, 255, 255)
            };
            for (int i = 0; i < 110; i++) {
                confettis.add(new ConfettiParticle(
                        random.nextInt(w),
                        random.nextInt(h) - h,
                        colors[random.nextInt(colors.length)],
                        random));
            }

            // สุ่มตำแหน่งประกายดาว 35 จุด
            sparkles.clear();
            for (int i = 0; i < 35; i++) {
                sparkles.add(new SparkleParticle(random.nextInt(w), random.nextInt(h), random));
            }

            setVisible(true);
            animTimer.start();
            revalidate();
            repaint();
        }

        // หยุดเอฟเฟกต์และล้างข้อมูล
        void stopCelebration() {
            active = false;
            animTimer.stop();
            setVisible(false);
            confettis.clear();
            sparkles.clear();
        }

        // คำนวณการตก การหมุน และการเกิดใหม่ของเศษกระดาษและแสง
        private void updatePhysics() {
            int w = getWidth();
            int h = getHeight();

            for (ConfettiParticle c : confettis) {
                c.y += c.speedY;
                c.x += c.speedX + (float) Math.sin(c.wobble) * 1.5f;
                c.wobble += c.wobbleSpeed;
                c.rotation += c.rotationSpeed;

                // เมื่อตกพ้นจอด้านล่าง ให้ย้ายกลับไปโปรยใหม่ด้านบน
                if (c.y > h + 20) {
                    c.y = -random.nextInt(40) - 10;
                    c.x = random.nextInt(Math.max(1, w));
                }
            }

            for (SparkleParticle s : sparkles) {
                s.phase += s.speed;
                if (s.phase > Math.PI) {
                    s.phase = 0;
                    s.x = random.nextInt(Math.max(1, w));
                    s.y = random.nextInt(Math.max(1, h));
                    s.maxRadius = random.nextInt(12) + 10;
                }
            }
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            if (!active) {
                return;
            }
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int width = getWidth();
            int height = getHeight();

            // 1. วาดเศษกระดาษ Confetti พลิกตัว 3 มิติ
            for (ConfettiParticle c : confettis) {
                Graphics2D cg = (Graphics2D) g.create();
                cg.translate(c.x, c.y);
                float scaleX = (float) Math.abs(Math.cos(c.rotation));
                cg.setColor(c.color);
                int curW = Math.max(2, (int) (c.w * scaleX));
                cg.fillRect(-curW / 2, -c.h / 2, curW, c.h);
                cg.dispose();
            }

            // 2. วาดแสงดาววิบวับ 4 แฉก
            for (SparkleParticle s : sparkles) {
                float alpha = (float) Math.sin(s.phase);
                if (alpha > 0.05f) {
                    Graphics2D sg = (Graphics2D) g.create();
                    sg.translate(s.x, s.y);
                    sg.setColor(new Color(255, 235, 120, (int) (alpha * 220)));

                    int rOut = (int) (s.maxRadius * alpha);
                    int rIn = Math.max(2, rOut / 4);

                    int[] xPts = { 0, rIn, rOut, rIn, 0, -rIn, -rOut, -rIn };
                    int[] yPts = { -rOut, -rIn, 0, rIn, rOut, rIn, 0, -rIn };
                    sg.fillPolygon(xPts, yPts, 8);

                    sg.setColor(new Color(255, 255, 255, (int) (alpha * 255)));
                    sg.fillOval(-rIn, -rIn, rIn * 2, rIn * 2);
                    sg.dispose();
                }
            }

            // 3. วาดการ์ดประกาศผู้ชนะตรงกลางหน้าจอ
            int cardW = 540;
            int cardH = 210;
            int cardX = (width - cardW) / 2;
            int cardY = (height - cardH) / 2;

            g.setColor(new Color(0, 0, 0, 100));
            g.fillRoundRect(cardX + 4, cardY + 6, cardW, cardH, 24, 24);

            g.setColor(new Color(16, 22, 32, 235));
            g.fillRoundRect(cardX, cardY, cardW, cardH, 24, 24);

            g.setColor(new Color(255, 215, 60, 220));
            g.setStroke(new BasicStroke(3));
            g.drawRoundRect(cardX, cardY, cardW, cardH, 24, 24);

            g.setFont(new Font(UI_FONT, Font.BOLD, 30));
            g.setColor(new Color(255, 215, 60));
            String titleStr = "VICTORY!";
            int titleW = g.getFontMetrics().stringWidth(titleStr);
            g.drawString(titleStr, cardX + (cardW - titleW) / 2, cardY + 54);

            // ข้อความบอกผู้ชนะ (ป้องกันการแสดงชื่อซ้ำ เช่น 'ผู้เล่น 1 (ผู้เล่น 1)')
            g.setFont(new Font(UI_FONT, Font.BOLD, 22));
            g.setColor(Color.WHITE);
            String winStr;
            if (winnerName == null || winnerName.trim().isEmpty() || winnerName.equals("ผู้เล่น " + winnerLane)) {
                winStr = "ผู้เล่นเลน " + winnerLane + " ชนะการแข่งขัน!";
            } else {
                winStr = winnerName + " (เลน " + winnerLane + ") ชนะการแข่งขัน!";
            }
            int winW = g.getFontMetrics().stringWidth(winStr);
            g.drawString(winStr, cardX + (cardW - winW) / 2, cardY + 108);

            // จัดตำแหน่งปุ่มเริ่มใหม่ให้อยู่กึ่งกลางการ์ด
            int btnW = 200;
            int btnH = 42;
            int btnX = cardX + (cardW - btnW) / 2;
            int btnY = cardY + 144;
            playAgainButton.setBounds(btnX, btnY, btnW, btnH);

            g.dispose();
        }
    }

    // ข้อมูลจำลองเม็ดกระดาษ Confetti
    private static class ConfettiParticle {
        float x, y;
        float speedX, speedY;
        float wobble, wobbleSpeed;
        float rotation, rotationSpeed;
        int w, h;
        Color color;

        ConfettiParticle(int x, int y, Color color, Random random) {
            this.x = x;
            this.y = y;
            this.color = color;
            this.speedY = 3.0f + random.nextFloat() * 4.0f;
            this.speedX = (random.nextFloat() - 0.5f) * 1.6f;
            this.wobble = random.nextFloat() * 6.28f;
            this.wobbleSpeed = 0.05f + random.nextFloat() * 0.08f;
            this.rotation = random.nextFloat() * 6.28f;
            this.rotationSpeed = 0.04f + random.nextFloat() * 0.09f;
            this.w = 9 + random.nextInt(6);
            this.h = 6 + random.nextInt(5);
        }
    }

    // ข้อมูลจำลองแสงดาววิบวับ
    private static class SparkleParticle {
        int x, y;
        int maxRadius;
        float phase;
        float speed;

        SparkleParticle(int x, int y, Random random) {
            this.x = x;
            this.y = y;
            this.maxRadius = random.nextInt(12) + 10;
            this.phase = random.nextFloat() * 3.14f;
            this.speed = 0.06f + random.nextFloat() * 0.08f;
        }
    }

    // =========================================================================
    // คลาสย่อย: TrackPanel (วาดสนามแข่งแต่ละเลน, เส้นสตาร์ท, เส้นชัย และตัวละคร)
    // =========================================================================
    private static class TrackPanel extends JPanel {
        private static final long serialVersionUID = 1L;
        private static final int RUNNER_SIZE = 72;
        private static final int START_X = 50;
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
            setPreferredSize(new Dimension(720, 160));
        }

        String getPlayerName() {
            return playerName;
        }

        void setSelected(boolean selected) {
            this.selected = selected;
            repaint();
        }

        // ก้าวไปข้างหน้า 1 สเต็ป และเช็กว่าถึงเส้นชัยหรือยัง
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
            return Math.max(0, getWidth() - RUNNER_SIZE - START_X - 52);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int width = getWidth();
            int height = getHeight();

            // 1. เส้นแบ่งเลนสีขาวและขีดบอกระยะ
            g.setColor(new Color(255, 255, 255, 230));
            g.setStroke(new BasicStroke(3));
            g.drawLine(0, 0, width, 0);
            g.drawLine(0, height - 1, width, height - 1);

            g.setColor(new Color(255, 255, 255, 45));
            g.setStroke(new BasicStroke(1));
            for (int x = START_X + 100; x < width - 70; x += 100) {
                g.drawLine(x, height - 14, x, height - 2);
            }

            // 2. เส้นสตาร์ทและตัวเลขเลนขนาดใหญ่บนพื้นสนาม
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(5));
            g.drawLine(START_X + 4, 0, START_X + 4, height);

            g.setColor(new Color(0, 0, 0, 50));
            g.fillRect(START_X - 18, (height - 30) / 2, 14, 30);

            g.setColor(new Color(255, 255, 255, 80));
            g.setFont(new Font("Impact", Font.PLAIN, 56));
            g.drawString(String.valueOf(laneNumber), START_X + 16, height / 2 + 20);

            // 3. กรอบสีเหลืองเน้นเลนที่เรากำลังเลือก/ควบคุม
            if (selected) {
                g.setColor(new Color(255, 220, 60));
                g.setStroke(new BasicStroke(3));
                g.drawRect(2, 2, width - 5, height - 5);
            }

            // 4. ป้ายชื่อผู้เล่นมุมซ้ายบน
            String labelText = playerName + (selected ? "  (คุณ)" : "");
            g.setFont(new Font(UI_FONT, Font.BOLD, 13));
            FontMetrics fm = g.getFontMetrics();
            int textW = fm.stringWidth(labelText);
            int badgeW = textW + 24;
            int badgeH = 26;
            g.setColor(new Color(15, 20, 28, 170));
            g.fillRoundRect(14, 10, badgeW, badgeH, 12, 12);
            g.setColor(selected ? new Color(255, 225, 75) : new Color(240, 245, 250));
            g.drawString(labelText, 26, 27);

            // 5. เส้นชัยลายตารางหมากรุก 2 แถวสลับขาว-ดำ พร้อมป้าย FINISH
            int finishX = width - 56;
            int checkW = 10;
            int checkH = 12;
            for (int y = 0; y < height; y += checkH) {
                int row = y / checkH;
                g.setColor((row % 2 == 0) ? new Color(25, 25, 25) : Color.WHITE);
                g.fillRect(finishX, y, checkW, checkH);
                g.setColor((row % 2 == 0) ? Color.WHITE : new Color(25, 25, 25));
                g.fillRect(finishX + checkW, y, checkW, checkH);
            }

            g.setColor(new Color(210, 218, 226));
            g.fillRoundRect(finishX - 3, 0, checkW * 2 + 6, 8, 4, 4);
            g.fillRoundRect(finishX - 3, height - 8, checkW * 2 + 6, 8, 4, 4);

            g.setColor(new Color(215, 35, 35));
            g.fillRoundRect(finishX - 8, 12, 36, 16, 6, 6);
            g.setColor(Color.WHITE);
            g.setFont(new Font("Arial", Font.BOLD, 9));
            g.drawString("FINISH", finishX - 5, 24);

            // 6. ริบบิ้นผ้าเส้นชัย (ถ้ายังไม่ถึงจะขึงตึง ถ้าแตะแล้วจะขาดสะบัด)
            boolean reachedFinish = progress >= getFinishProgress();
            if (!reachedFinish) {
                g.setColor(new Color(230, 35, 45));
                g.setStroke(new BasicStroke(4));
                g.drawLine(finishX - 1, 0, finishX - 1, height);

                g.setColor(new Color(255, 80, 90));
                g.fillOval(finishX - 4, height / 2 - 4, 7, 8);
            } else {
                g.setColor(new Color(230, 35, 45));
                g.setStroke(new BasicStroke(3));
                g.drawLine(finishX - 1, 0, finishX - 6, height / 3);
                g.drawLine(finishX - 6, height / 3, finishX + 10, height / 2 - 12);
                g.drawLine(finishX - 1, height, finishX - 8, height * 2 / 3);
                g.drawLine(finishX - 8, height * 2 / 3, finishX + 12, height / 2 + 14);
            }

            // 7. วาดเงาและรูปตัวละครนักวิ่ง
            int runnerY = Math.max(28, (height - RUNNER_SIZE) / 2);
            if (playerActive) {
                g.setColor(new Color(0, 0, 0, 55));
                g.fillOval(START_X + progress + 8, runnerY + RUNNER_SIZE - 6, RUNNER_SIZE - 16, 12);

                g.drawImage(runnerImage, START_X + progress, runnerY, RUNNER_SIZE, RUNNER_SIZE, null);
            }

            g.dispose();
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new Game().setVisible(true));
    }
}