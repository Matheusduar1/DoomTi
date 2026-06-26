package doomti;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.util.ArrayList;
import java.util.Random;
import javax.imageio.ImageIO;

public class GamePanel extends JPanel implements Runnable, KeyListener, MouseListener, MouseMotionListener {
    
    enum State { MENU, NAME_INPUT, OPTIONS, PLAYING, PAUSED, NPC_CHAT, CMD_VIRTUAL, EASTER_EGG }
    State currentState = State.MENU;
    State internalEasterEgg = State.PLAYING; 

    int screenWidth = 800;
    int screenHeight = 600;
    
    String playerName = "";
    double posX = 4.5, posY = 4.5; 
    double dirX = -1.0, dirY = 0.0; 
    double planeX = 0.0, planeY = 0.66; 
    
    boolean moveForward, moveBackward, rotateLeft, rotateRight;
    boolean isPunching = false;
    int punchFrame = 0;

    double[] zBuffer;
    int[] wallTypeBuffer; 
    
    BufferedImage floorBuffer;
    int[] floorPixels;
    int[] floorTexArray;

    int[][] worldMap = {
        {1,1,1,1,1,1,1,1,1,4,1},
        {1,0,3,0,0,0,0,0,0,0,1},
        {1,2,2,0,0,0,0,0,0,0,1},
        {1,0,0,0,0,0,0,0,0,0,1},
        {1,0,0,0,0,0,0,0,0,0,1},
        {1,5,0,0,5,2,5,0,0,5,1},
        {1,5,0,0,5,2,5,0,0,5,1},
        {1,5,0,0,5,2,5,0,0,5,1},
        {1,1,1,1,1,1,1,1,1,1,1}
    };

    BufferedImage texWall1, texWall4, texTable, texFloor, texPunch;
    ArrayList<Entity> entities = new ArrayList<>();

    String currentDir = "C:\\Users\\";
    ArrayList<String> cmdHistory = new ArrayList<>();
    String cmdInput = "";
    
    String professorResponse = "Olá! Seja bem-vindo ao laboratório de TI. O que gostaria de aprender hoje?";
    String playerChatInput = "";
    boolean loadingAI = false;

    int menuSelection = 0;
    int optionsSelection = 0;
    int pauseSelection = 0;
    JComboBox<String> resBox;

    int gameX = 400, gameY = 300;
    int gameTargetX = 450, gameTargetY = 320;
    int gameScore = 0;
    int gameObstacleX = 650;
    boolean gameJump = false;
    int jumpSpeed = 0;
    Random rand = new Random();

    public GamePanel() {
        setPreferredSize(new Dimension(screenWidth, screenHeight));
        setFocusable(true);
        addKeyListener(this);
        addMouseListener(this);
        addMouseMotionListener(this);
        
        initBuffers();
        loadGameAssets();
        
        String[] resolutions = { "800x600", "1280x720 (HD)", "1920x1080 (FHD)" };
        resBox = new JComboBox<>(resolutions);
        resBox.setBounds(350, 250, 150, 30);
        resBox.setVisible(false);
        this.setLayout(null);
        this.add(resBox);

        new Thread(this).start();
    }

    private void initBuffers() {
        zBuffer = new double[screenWidth];
        wallTypeBuffer = new int[screenWidth];
        floorBuffer = new BufferedImage(screenWidth, screenHeight / 2, BufferedImage.TYPE_INT_RGB);
        floorPixels = ((DataBufferInt) floorBuffer.getRaster().getDataBuffer()).getData();
    }

    private void loadGameAssets() {
        texWall1 = loadOrCreateFallback("sprites/wall1.png", Color.DARK_GRAY, true, 128, 128);
        texWall4 = loadOrCreateFallback("sprites/wall4.png", Color.BLUE, true, 128, 128);
        texTable = loadOrCreateFallback("sprites/table.png", new Color(139, 69, 19), true, 128, 128);
        texFloor = loadOrCreateFallback("sprites/floor.png", new Color(50, 50, 50), true, 128, 128);
        texPunch = loadOrCreateFallback("sprites/punch.png", new Color(245, 222, 179), false, 256, 256);
        
        BufferedImage texNPC = loadOrCreateFallback("sprites/npc.png", Color.GREEN, false, 500, 500);
        BufferedImage texPC = loadOrCreateFallback("sprites/pc.png", Color.LIGHT_GRAY, false, 32, 32);

        floorTexArray = texFloor.getRGB(0, 0, 128, 128, null, 0, 128);

        for(int x = 0; x < worldMap.length; x++){
            for(int y = 0; y < worldMap[0].length; y++){
                if(worldMap[x][y] == 3) {
                    entities.add(new Entity(x + 0.5, y + 0.5, 3, 0.9, 0.0, texNPC));
                    worldMap[x][y] = 0; 
                } else if(worldMap[x][y] == 5) {
                    entities.add(new Entity(x + 0.5, y + 0.5, 5, 0.25, 0.22, texPC));
                    worldMap[x][y] = 2; 
                }
            }
        }
    }

    private BufferedImage loadOrCreateFallback(String path, Color fallbackColor, boolean gridPattern, int w, int h) {
        try {
            File f = new File(path);
            if (f.exists()) {
                BufferedImage img = ImageIO.read(f);
                BufferedImage argb = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
                argb.getGraphics().drawImage(img, 0, 0, null);
                return argb;
            }
        } catch (Exception e) {}
        
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(fallbackColor);
        g.fillRect(0, 0, w, h);
        if (gridPattern) {
            g.setColor(Color.BLACK);
            g.drawRect(0, 0, w - 1, h - 1);
            g.drawLine(0, 0, w, h);
        }
        g.dispose();
        return img;
    }

    @Override
    public void run() {
        while (true) {
            updateGameLogic();
            repaint();
            try { Thread.sleep(16); } catch (InterruptedException e) {}
        }
    }

    private void updateGameLogic() {
        if (currentState == State.PLAYING) {
            double moveSpeed = 0.06;
            double rotSpeed = 0.04;
            // Margem de colisão aumentada para manter o jogador longe do ponto cego matemático da textura
            double colBuffer = 0.45; 

            if (moveForward) {
                if (worldMap[(int)(posX + dirX * moveSpeed + dirX * colBuffer)][(int)posY] == 0) posX += dirX * moveSpeed;
                if (worldMap[(int)posX][(int)(posY + dirY * moveSpeed + dirY * colBuffer)] == 0) posY += dirY * moveSpeed;
            }
            if (moveBackward) {
                if (worldMap[(int)(posX - dirX * moveSpeed - dirX * colBuffer)][(int)posY] == 0) posX -= dirX * moveSpeed;
                if (worldMap[(int)posX][(int)(posY - dirY * moveSpeed - dirY * colBuffer)] == 0) posY -= dirY * moveSpeed;
            }
            if (rotateRight) {
                double oldDirX = dirX;
                dirX = dirX * Math.cos(-rotSpeed) - dirY * Math.sin(-rotSpeed);
                dirY = oldDirX * Math.sin(-rotSpeed) + dirY * Math.cos(-rotSpeed);
                double oldPlaneX = planeX;
                planeX = planeX * Math.cos(-rotSpeed) - planeY * Math.sin(-rotSpeed);
                planeY = oldPlaneX * Math.sin(-rotSpeed) + planeY * Math.cos(-rotSpeed);
            }
            if (rotateLeft) {
                double oldDirX = dirX;
                dirX = dirX * Math.cos(rotSpeed) - dirY * Math.sin(rotSpeed);
                dirY = oldDirX * Math.sin(rotSpeed) + dirY * Math.cos(rotSpeed);
                double oldPlaneX = planeX;
                planeX = planeX * Math.cos(rotSpeed) - planeY * Math.sin(rotSpeed);
                planeY = oldPlaneX * Math.sin(rotSpeed) + planeY * Math.cos(rotSpeed);
            }
            if (isPunching) {
                punchFrame++;
                if (punchFrame > 10) { isPunching = false; punchFrame = 0; }
            }
        } else if (currentState == State.EASTER_EGG) {
            updateEasterEggLogic();
        }
    }

    private void updateEasterEggLogic() {
        if (internalEasterEgg == State.MENU) { 
            gameX += jumpSpeed; 
            gameY += (isPunching ? 4 : 0); 
            if (gameX < 120 || gameX > 660) jumpSpeed *= -1;
            if (Math.abs(gameX - gameTargetX) < 25 && Math.abs(gameY - gameTargetY) < 25) {
                gameScore++;
                gameTargetX = rand.nextInt(400) + 150;
                gameTargetY = rand.nextInt(200) + 150;
            }
        } else if (internalEasterEgg == State.NAME_INPUT) { 
            gameY += 3; 
            if (gameY > 430) {
                gameY = 120;
                gameX = rand.nextInt(400) + 150;
                gameScore += 10;
            }
        } else if (internalEasterEgg == State.OPTIONS) { 
            gameTargetX += rand.nextInt(7) - 3;
            if (gameTargetX < 150 || gameTargetX > 630) gameTargetX = 350;
        } else if (internalEasterEgg == State.PLAYING) { 
            gameObstacleX -= 6;
            if (gameObstacleX < 110) {
                gameObstacleX = 660;
                gameScore++;
            }
            if (gameJump) {
                gameY += jumpSpeed;
                jumpSpeed += 1; 
                if (gameY >= 350) {
                    gameY = 350;
                    gameJump = false;
                }
            }
            if (gameObstacleX >= gameX - 10 && gameObstacleX <= gameX + 20 && gameY >= 330) {
                gameScore = 0; 
            }
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;

        switch (currentState) {
            case MENU: drawMainMenu(g2d); break;
            case NAME_INPUT: drawNameInput(g2d); break;
            case OPTIONS: drawOptions(g2d); break;
            case PAUSED: drawPauseMenu(g2d); break;
            case PLAYING: 
            case NPC_CHAT:
            case CMD_VIRTUAL:
            case EASTER_EGG:
                renderRaycasting3D(g2d);
                renderHUD(g2d);
                if (currentState == State.NPC_CHAT) renderNPCChatWindow(g2d);
                if (currentState == State.CMD_VIRTUAL) renderVirtualCMD(g2d);
                if (currentState == State.EASTER_EGG) renderEasterEggGame(g2d);
                break;
        }
    }

    private void renderRaycasting3D(Graphics2D g2d) {
        g2d.setColor(new Color(30, 30, 30));
        g2d.fillRect(0, 0, screenWidth, screenHeight / 2);

        for (int y = 0; y < screenHeight / 2; y++) {
            int p = y + 1; 
            float posZ = 0.5f * screenHeight;
            float rowDist = posZ / p;

            float rayDirX0 = (float)(dirX - planeX);
            float rayDirY0 = (float)(dirY - planeY);
            float rayDirX1 = (float)(dirX + planeX);
            float rayDirY1 = (float)(dirY + planeY);

            float stepX = rowDist * (rayDirX1 - rayDirX0) / screenWidth;
            float stepY = rowDist * (rayDirY1 - rayDirY0) / screenWidth;

            float fX = (float)posX + rowDist * rayDirX0;
            float fY = (float)posY + rowDist * rayDirY0;

            for (int x = 0; x < screenWidth; x++) {
                int cellX = (int)fX; int cellY = (int)fY;
                int tx = (int)(128 * (fX - cellX)) & 127;
                int ty = (int)(128 * (fY - cellY)) & 127;

                fX += stepX; fY += stepY;
                floorPixels[y * screenWidth + x] = floorTexArray[128 * ty + tx];
            }
        }
        g2d.drawImage(floorBuffer, 0, screenHeight / 2, null);

        for (int x = 0; x < screenWidth; x++) {
            double cameraX = 2 * x / (double) screenWidth - 1;
            double rayDirX = dirX + planeX * cameraX;
            double rayDirY = dirY + planeY * cameraX;

            int mapX = (int) posX;
            int mapY = (int) posY;

            double sideDistX, sideDistY;
            double deltaDistX = Math.abs(1 / rayDirX);
            double deltaDistY = Math.abs(1 / rayDirY);
            double perpWallDist;

            int stepX, stepY;
            boolean hit = false;
            int side = 0; 

            if (rayDirX < 0) { stepX = -1; sideDistX = (posX - mapX) * deltaDistX; } 
            else { stepX = 1; sideDistX = (mapX + 1.0 - posX) * deltaDistX; }
            if (rayDirY < 0) { stepY = -1; sideDistY = (posY - mapY) * deltaDistY; } 
            else { stepY = 1; sideDistY = (mapY + 1.0 - posY) * deltaDistY; }

            while (!hit) {
                if (sideDistX < sideDistY) { sideDistX += deltaDistX; mapX += stepX; side = 0; } 
                else { sideDistY += deltaDistY; mapY += stepY; side = 1; }
                if (worldMap[mapX][mapY] > 0) hit = true;
            }

            if (side == 0) perpWallDist = (mapX - posX + (1 - stepX) / 2) / rayDirX;
            else           perpWallDist = (mapY - posY + (1 - stepY) / 2) / rayDirY;

            zBuffer[x] = perpWallDist; 
            wallTypeBuffer[x] = worldMap[mapX][mapY]; 

            int lineHeight = (int) (screenHeight / perpWallDist);
            int drawStart = -lineHeight / 2 + screenHeight / 2;
            int drawEnd = lineHeight / 2 + screenHeight / 2;

            int blockID = worldMap[mapX][mapY];
            BufferedImage currentTex = (blockID == 4) ? texWall4 : texWall1;
            
            double wallX = (side == 0) ? posY + perpWallDist * rayDirY : posX + perpWallDist * rayDirX;
            wallX -= Math.floor(wallX);

            int texX = (int) (wallX * 128.0);
            if (side == 0 && rayDirX > 0) texX = 128 - texX - 1;
            if (side == 1 && rayDirY < 0) texX = 128 - texX - 1;

            if (blockID == 2) { 
                drawStart = (screenHeight / 2) + (lineHeight / 5);
                
                // --- NOVO: DESENHA O TAMPO BEGE DA BANCADA ---
                if (drawStart > screenHeight / 2) {
                    g2d.setColor(new Color(222, 184, 135)); // Cor Burlywood (Bege amadeirado)
                    g2d.drawLine(x, screenHeight / 2, x, drawStart);
                }
                
                currentTex = texTable;
            }

            if (drawStart < 0) drawStart = 0;
            if (drawEnd >= screenHeight) drawEnd = screenHeight - 1;

            g2d.drawImage(currentTex, x, drawStart, x + 1, drawEnd, texX, 0, texX + 1, 128, null);
        }
        
        renderSprites(g2d);
    }

    private void renderSprites(Graphics2D g2d) {
        entities.sort((e1, e2) -> {
            double d1 = Math.pow(posX - e1.x, 2) + Math.pow(posY - e1.y, 2);
            double d2 = Math.pow(posX - e2.x, 2) + Math.pow(posY - e2.y, 2);
            return Double.compare(d2, d1);
        });

        for(Entity ent : entities) {
            double spriteX = ent.x - posX;
            double spriteY = ent.y - posY;

            double invDet = 1.0 / (planeX * dirY - dirX * planeY);
            double transformX = invDet * (dirY * spriteX - dirX * spriteY);
            double transformY = invDet * (-planeY * spriteX + planeX * spriteY); 

            // Adicionado limite seguro (0.1) para evitar que transformY próximo a zero gere sprites gigantes
            if (transformY > 0.1) {
                int spriteScreenX = (int) ((screenWidth / 2) * (1 + transformX / transformY));
                
                int texW = ent.tex.getWidth();
                int texH = ent.tex.getHeight();

                int stdHeight = Math.abs((int) (screenHeight / transformY)); 
                // Trava de segurança para impedir distorção quando estiver "dentro" do bloco
                if (stdHeight > 3000) stdHeight = 3000;
                
                int spriteHeight = (int)(stdHeight * ent.vScale);
                
                double aspect = (double) texW / texH;
                int spriteWidth = (int)(spriteHeight * aspect); 
                
                int baseEndY = screenHeight / 2 + stdHeight / 2;
                int drawEndY = baseEndY - (int)(ent.vOffset * stdHeight);
                int drawStartY = drawEndY - spriteHeight;

                int drawStartX = -spriteWidth / 2 + spriteScreenX;
                int drawEndX = spriteWidth / 2 + spriteScreenX;

                for (int stripe = drawStartX; stripe < drawEndX; stripe++) {
                    if (stripe >= 0 && stripe < screenWidth) {
                        boolean isVisible = transformY < zBuffer[stripe];
                        if (!isVisible && wallTypeBuffer[stripe] == 2 && transformY < zBuffer[stripe] + 1.0) {
                            isVisible = true; 
                        }

                        if (isVisible) {
                            int texX = (stripe - drawStartX) * texW / spriteWidth;
                            if(texX >= 0 && texX < texW) {
                                g2d.drawImage(ent.tex, stripe, drawStartY, stripe + 1, drawEndY, 
                                              texX, 0, texX + 1, texH, null);
                            }
                        }
                    }
                }

                if (ent.type == 3 && currentState == State.PLAYING && transformY < 4.0) {
                    g2d.setColor(Color.WHITE);
                    g2d.fillRoundRect(spriteScreenX - 100, drawStartY - 55, 200, 45, 10, 10);
                    g2d.setColor(Color.BLACK);
                    g2d.setFont(new Font("Arial", Font.BOLD, 11));
                    g2d.drawString("Prof. de TI", spriteScreenX - 90, drawStartY - 35);
                    g2d.setFont(new Font("Arial", Font.PLAIN, 10));
                    g2d.drawString("Clique Direito p/ falar...", spriteScreenX - 90, drawStartY - 20);
                }
            }
        }
    }

    private void renderHUD(Graphics2D g2d) {
        g2d.setColor(Color.RED);
        g2d.drawLine(screenWidth / 2 - 5, screenHeight / 2, screenWidth / 2 + 5, screenHeight / 2);
        g2d.drawLine(screenWidth / 2, screenHeight / 2 - 5, screenWidth / 2, screenHeight / 2 + 5);

        g2d.setColor(Color.DARK_GRAY);
        g2d.fillRect(0, screenHeight - 50, screenWidth, 50);
        g2d.setColor(Color.WHITE);
        g2d.drawRect(5, screenHeight - 45, screenWidth - 10, 40);
        
        g2d.setFont(new Font("Courier New", Font.BOLD, 16));
        g2d.drawString("USER: " + playerName.toUpperCase(), 20, screenHeight - 20);
        g2d.drawString("STATUS: ONLINE", screenWidth - 200, screenHeight - 20);

        int punchYOffset = isPunching ? (punchFrame <= 5 ? punchFrame * 15 : (10 - punchFrame) * 15) : 0;
        int armWidth = 160; int armHeight = 160;
        int armX = (screenWidth / 2) - (armWidth / 2);
        int armY = screenHeight - 50 - armHeight + punchYOffset;

        g2d.drawImage(texPunch, armX, armY, armWidth, armHeight, null);
    }

    private void callProfessorAI(String message) {
        loadingAI = true;
        professorResponse = "Pensando...";
        ProfessorAI.ask(playerName, message, new ProfessorAI.AICallback() {
            @Override
            public void onResponse(String response) { professorResponse = response; loadingAI = false; }
            @Override
            public void onError(String error) { professorResponse = error; loadingAI = false; }
        });
    }

    private void renderNPCChatWindow(Graphics2D g2d) { 
        g2d.setColor(new Color(0, 0, 0, 220));
        g2d.fillRect(50, screenHeight - 220, screenWidth - 100, 160);
        g2d.setColor(Color.GREEN);
        g2d.drawRect(50, screenHeight - 220, screenWidth - 100, 160);
        g2d.setFont(new Font("Arial", Font.BOLD, 14));
        g2d.drawString("PROFESSOR DE TI:", 70, screenHeight - 195);
        g2d.setFont(new Font("Arial", Font.PLAIN, 12));
        
        String[] words = professorResponse.split(" ");
        StringBuilder line = new StringBuilder();
        int yOffset = screenHeight - 175;
        for(String word : words) {
            if(line.length() + word.length() > 80) {
                g2d.drawString(line.toString(), 70, yOffset);
                line = new StringBuilder();
                yOffset += 18;
            }
            line.append(word).append(" ");
        }
        g2d.drawString(line.toString(), 70, yOffset);

        g2d.setColor(Color.DARK_GRAY);
        g2d.fillRect(70, screenHeight - 95, screenWidth - 140, 25);
        g2d.setColor(Color.WHITE);
        g2d.drawString("Sua Mensagem: " + playerChatInput + "_", 80, screenHeight - 78);
    }

    private void renderVirtualCMD(Graphics2D g2d) { 
        g2d.setColor(Color.BLACK); g2d.fillRect(0, 0, screenWidth, screenHeight - 50);
        g2d.setColor(Color.WHITE); g2d.setFont(new Font("Courier New", Font.PLAIN, 14));
        g2d.drawString("Microsoft Windows Virtual [Versão 10.0.19045]", 20, 30);
        int y = 70;
        for (String line : cmdHistory) { g2d.drawString(line, 20, y); y += 20; }
        g2d.drawString(currentDir + playerName + "> " + cmdInput + "_", 20, y);
    }

    private void executeCMDCommand(String fullCmd) {
        cmdHistory.add(currentDir + playerName + "> " + fullCmd);
        String clean = fullCmd.trim().toLowerCase();
        
        gameScore = 0; gameX = 200; gameY = 350; gameObstacleX = 650; jumpSpeed = 4;
        
        if (clean.equals("dir")) {
            if (currentDir.endsWith("Downloads\\")) {
                cmdHistory.add("25/06/2026  20:15    <EXE>      pacman.exe");
                cmdHistory.add("25/06/2026  20:15    <EXE>      tetris.exe");
                cmdHistory.add("25/06/2026  20:15    <EXE>      doom.exe");
                cmdHistory.add("25/06/2026  20:15    <EXE>      dino.exe");
            } else {
                cmdHistory.add("25/06/2026  20:00    <DIR>      Downloads");
            }
        } else if (clean.equals("cd downloads")) {
            if (!currentDir.endsWith("Downloads\\")) currentDir += "Downloads\\";
        } else if (clean.equals("cd ..")) {
            if (currentDir.endsWith("Downloads\\")) currentDir = "C:\\Users\\";
        } else if ((clean.equals("pacman.exe") || clean.equals("pacman")) && currentDir.endsWith("Downloads\\")) {
            currentState = State.EASTER_EGG; internalEasterEgg = State.MENU;
        } else if ((clean.equals("tetris.exe") || clean.equals("tetris")) && currentDir.endsWith("Downloads\\")) {
            currentState = State.EASTER_EGG; internalEasterEgg = State.NAME_INPUT; gameY = 120;
        } else if ((clean.equals("doom.exe") || clean.equals("doom")) && currentDir.endsWith("Downloads\\")) {
            currentState = State.EASTER_EGG; internalEasterEgg = State.OPTIONS; gameX = 400; gameY = 300;
        } else if ((clean.equals("dino.exe") || clean.equals("dino")) && currentDir.endsWith("Downloads\\")) {
            currentState = State.EASTER_EGG; internalEasterEgg = State.PLAYING; gameY = 350;
        } else if (clean.equals("cls")) {
            cmdHistory.clear();
        } else if (clean.equals("exit")) {
            currentState = State.PLAYING;
        } else {
            cmdHistory.add("Comando não reconhecido. Digite 'dir' ou utilize TAB.");
        }
    }

    private void renderEasterEggGame(Graphics2D g2d) { 
        g2d.setColor(Color.BLUE); g2d.fillRect(100, 100, screenWidth - 200, screenHeight / 2 + 50);
        g2d.setColor(Color.WHITE); g2d.drawRect(100, 100, screenWidth - 200, screenHeight / 2 + 50);
        
        g2d.setFont(new Font("Courier New", Font.BOLD, 18));
        g2d.drawString("SCORE: " + gameScore, 120, 130);

        if (internalEasterEgg == State.MENU) { 
            g2d.drawString("[MINI PACMAN] Use Setas do Teclado", 300, 130);
            g2d.setColor(Color.YELLOW);
            g2d.fillArc(gameX, gameY, 30, 30, 30, 300); 
            g2d.setColor(Color.RED);
            g2d.fillOval(gameTargetX, gameTargetY, 15, 15); 
        } 
        else if (internalEasterEgg == State.NAME_INPUT) { 
            g2d.drawString("[MINI TETRIS] Seta Esquerda/Direita", 300, 130);
            g2d.setColor(Color.ORANGE);
            g2d.fillRect(gameX, gameY, 20, 40); 
            g2d.setColor(Color.GRAY);
            g2d.fillRect(100, 445, 600, 5); 
        } 
        else if (internalEasterEgg == State.OPTIONS) { 
            g2d.drawString("[DOOM 2D] Seta Esq/Dir p/ mirar e ESPAÇO p/ atirar", 150, 130);
            g2d.setColor(Color.GREEN);
            g2d.fillRect(gameX, 400, 30, 30); 
            g2d.setColor(Color.RED);
            g2d.fillRect(gameTargetX, 180, 25, 25); 
            if (isPunching) { 
                g2d.setColor(Color.YELLOW);
                g2d.drawLine(gameX + 15, 400, gameTargetX + 12, 205);
            }
        } 
        else if (internalEasterEgg == State.PLAYING) { 
            g2d.drawString("[GOOGLE DINO] Pressione ESPAÇO ou Seta Cima p/ Pular", 180, 130);
            g2d.setColor(Color.WHITE);
            g2d.fillRect(gameX, gameY, 20, 40); 
            g2d.setColor(Color.GREEN);
            g2d.fillRect(gameObstacleX, 360, 15, 30); 
            g2d.setColor(Color.DARK_GRAY);
            g2d.fillRect(100, 390, 600, 2); 
        }

        g2d.setFont(new Font("Arial", Font.PLAIN, 12));
        g2d.setColor(Color.WHITE);
        g2d.drawString("Pressione ESC para fechar o jogo e retornar ao CMD.", 240, 430);
    }

    private void drawMainMenu(Graphics2D g2d) {
        g2d.setColor(Color.BLACK); g2d.fillRect(0, 0, screenWidth, screenHeight);
        g2d.setColor(Color.RED); g2d.setFont(new Font("Courier New", Font.BOLD, 36));
        g2d.drawString("RAYCASTING: LAB-TI", 220, 150);
        String[] options = { "JOGAR", "OPÇÕES", "SAIR" };
        for (int i = 0; i < options.length; i++) {
            g2d.setColor((i == menuSelection) ? Color.YELLOW : Color.WHITE);
            g2d.setFont(new Font("Arial", Font.BOLD, 22));
            g2d.drawString(options[i], 350, 280 + (i * 50));
        }
    }

    private void drawNameInput(Graphics2D g2d) { 
        g2d.setColor(Color.BLACK); g2d.fillRect(0, 0, screenWidth, screenHeight);
        g2d.setColor(Color.WHITE); g2d.setFont(new Font("Arial", Font.BOLD, 24));
        g2d.drawString("Digite seu nome (Até 11 caracteres):", 200, 250);
        g2d.setColor(Color.YELLOW); g2d.drawString(playerName + "_", 350, 310);
    }

    private void drawOptions(Graphics2D g2d) { 
        g2d.setColor(Color.BLACK); g2d.fillRect(0, 0, screenWidth, screenHeight);
        g2d.setColor(Color.WHITE); g2d.setFont(new Font("Arial", Font.BOLD, 24));
        g2d.drawString("OPÇÕES DE RESOLUÇÃO", 260, 150);
        g2d.setColor((optionsSelection == 0) ? Color.YELLOW : Color.WHITE); g2d.drawString("Resolução:", 200, 270);
        g2d.setColor((optionsSelection == 1) ? Color.YELLOW : Color.WHITE); g2d.drawString("Voltar para o Menu", 200, 380);
    }

    private void drawPauseMenu(Graphics2D g2d) { 
        g2d.setColor(new Color(0,0,0,150)); g2d.fillRect(0, 0, screenWidth, screenHeight);
        g2d.setColor(Color.WHITE); g2d.setFont(new Font("Arial", Font.BOLD, 32));
        g2d.drawString("JOGO PAUSADO", 280, 200);
        String[] pOptions = { "RESUMIR JOGO", "SAIR PARA O MENU" };
        for (int i = 0; i < pOptions.length; i++) {
            g2d.setColor((i == pauseSelection) ? Color.YELLOW : Color.WHITE);
            g2d.setFont(new Font("Arial", Font.BOLD, 22));
            g2d.drawString(pOptions[i], 300, 320 + (i * 50));
        }
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        int my = e.getY();
        if (currentState == State.MENU) {
            if (my >= 250 && my <= 290) menuSelection = 0;
            else if (my >= 300 && my <= 340) menuSelection = 1;
            else if (my >= 350 && my <= 390) menuSelection = 2;
        } else if (currentState == State.PAUSED) {
            if (my >= 290 && my <= 330) pauseSelection = 0;
            else if (my >= 340 && my <= 380) pauseSelection = 1;
        } else if (currentState == State.OPTIONS) {
            if (my >= 240 && my <= 280) optionsSelection = 0;
            else if (my >= 350 && my <= 390) optionsSelection = 1;
        }
    }

    @Override
    public void mousePressed(MouseEvent e) {
        if (SwingUtilities.isLeftMouseButton(e)) {
            if (currentState == State.MENU) {
                if (menuSelection == 0) currentState = State.NAME_INPUT;
                else if (menuSelection == 1) { currentState = State.OPTIONS; resBox.setVisible(true); }
                else if (menuSelection == 2) System.exit(0);
            } else if (currentState == State.PAUSED) {
                if (pauseSelection == 0) currentState = State.PLAYING;
                else currentState = State.MENU;
            } else if (currentState == State.OPTIONS && optionsSelection == 1) {
                resBox.setVisible(false); currentState = State.MENU;
            } else if (currentState == State.PLAYING && !isPunching) {
                isPunching = true; punchFrame = 0;
            }
        } 
        else if (SwingUtilities.isRightMouseButton(e) && currentState == State.PLAYING) {
            for(Entity ent : entities) {
                double dist = Math.sqrt(Math.pow(posX - ent.x, 2) + Math.pow(posY - ent.y, 2));
                if(dist < 1.8) { 
                    if(ent.type == 3) currentState = State.NPC_CHAT;
                    else if(ent.type == 5) {
                        currentState = State.CMD_VIRTUAL;
                        if(cmdHistory.isEmpty()) cmdHistory.add("Computador Ligado.");
                    }
                }
            }
        }
    }

    @Override
    public void keyPressed(KeyEvent e) {
        int code = e.getKeyCode();
        
        if (currentState == State.CMD_VIRTUAL && code == KeyEvent.VK_TAB) {
            String input = cmdInput.trim().toLowerCase();
            if (currentDir.endsWith("Downloads\\")) {
                if (input.startsWith("pa")) cmdInput = "pacman.exe";
                else if (input.startsWith("te")) cmdInput = "tetris.exe";
                else if (input.startsWith("doo")) cmdInput = "doom.exe";
                else if (input.startsWith("di")) cmdInput = "dino.exe";
                else cmdInput = "pacman.exe"; 
            } else {
                if (input.startsWith("cd") || input.startsWith("cd d")) cmdInput = "cd Downloads";
            }
            e.consume();
            return;
        }

        if (currentState == State.MENU) {
            if (code == KeyEvent.VK_UP) menuSelection = (menuSelection - 1 + 3) % 3;
            if (code == KeyEvent.VK_DOWN) menuSelection = (menuSelection + 1) % 3;
            if (code == KeyEvent.VK_ENTER) {
                if (menuSelection == 0) currentState = State.NAME_INPUT;
                else if (menuSelection == 1) { currentState = State.OPTIONS; resBox.setVisible(true); }
                else if (menuSelection == 2) System.exit(0);
            }
        } else if (currentState == State.NAME_INPUT) {
            if (code == KeyEvent.VK_ENTER && !playerName.trim().isEmpty()) currentState = State.PLAYING;
            else if (code == KeyEvent.VK_BACK_SPACE && playerName.length() > 0) playerName = playerName.substring(0, playerName.length() - 1);
            else if (playerName.length() < 11 && Character.isLetterOrDigit(e.getKeyChar())) playerName += e.getKeyChar();
        } else if (currentState == State.OPTIONS) {
            if (code == KeyEvent.VK_UP) optionsSelection = (optionsSelection - 1 + 2) % 2;
            if (code == KeyEvent.VK_DOWN) optionsSelection = (optionsSelection + 1) % 2;
            if (code == KeyEvent.VK_ENTER) {
                if (optionsSelection == 0) {
                    String selectedRes = (String) resBox.getSelectedItem();
                    if (selectedRes.startsWith("800")) { screenWidth = 800; screenHeight = 600; }
                    else if (selectedRes.startsWith("1280")) { screenWidth = 1280; screenHeight = 720; }
                    else { screenWidth = 1920; screenHeight = 1080; }
                    initBuffers(); 
                    setPreferredSize(new Dimension(screenWidth, screenHeight));
                    JFrame topFrame = (JFrame) SwingUtilities.getWindowAncestor(this);
                    topFrame.pack(); topFrame.setLocationRelativeTo(null);
                } else { resBox.setVisible(false); currentState = State.MENU; }
            }
        } else if (currentState == State.PAUSED) {
            if (code == KeyEvent.VK_UP) pauseSelection = (pauseSelection - 1 + 2) % 2;
            if (code == KeyEvent.VK_DOWN) pauseSelection = (pauseSelection + 1) % 2;
            if (code == KeyEvent.VK_ENTER) {
                if (pauseSelection == 0) currentState = State.PLAYING;
                else currentState = State.MENU;
            }
            if (code == KeyEvent.VK_ESCAPE) currentState = State.PLAYING;
        } else if (currentState == State.PLAYING) {
            if (code == KeyEvent.VK_W || code == KeyEvent.VK_UP) moveForward = true;
            if (code == KeyEvent.VK_S || code == KeyEvent.VK_DOWN) moveBackward = true;
            if (code == KeyEvent.VK_A || code == KeyEvent.VK_LEFT) rotateLeft = true;
            if (code == KeyEvent.VK_D || code == KeyEvent.VK_RIGHT) rotateRight = true;
            if (code == KeyEvent.VK_ESCAPE) currentState = State.PAUSED;
        } else if (currentState == State.NPC_CHAT) {
            if (code == KeyEvent.VK_ESCAPE) currentState = State.PLAYING;
            else if (code == KeyEvent.VK_ENTER && !playerChatInput.trim().isEmpty() && !loadingAI) {
                callProfessorAI(playerChatInput); playerChatInput = "";
            } else if (code == KeyEvent.VK_BACK_SPACE && playerChatInput.length() > 0) playerChatInput = playerChatInput.substring(0, playerChatInput.length() - 1);
            else if (playerChatInput.length() < 60 && e.getKeyChar() != KeyEvent.CHAR_UNDEFINED && code != KeyEvent.VK_BACK_SPACE) playerChatInput += e.getKeyChar();
        } else if (currentState == State.CMD_VIRTUAL) {
            if (code == KeyEvent.VK_ENTER) { executeCMDCommand(cmdInput); cmdInput = ""; } 
            else if (code == KeyEvent.VK_BACK_SPACE && cmdInput.length() > 0) cmdInput = cmdInput.substring(0, cmdInput.length() - 1);
            else if (e.getKeyChar() != KeyEvent.CHAR_UNDEFINED && code != KeyEvent.VK_BACK_SPACE) cmdInput += e.getKeyChar();
            if (code == KeyEvent.VK_ESCAPE) currentState = State.PLAYING;
        } else if (currentState == State.EASTER_EGG) {
            if (code == KeyEvent.VK_ESCAPE) currentState = State.CMD_VIRTUAL;
            
            if (internalEasterEgg == State.MENU) { 
                if (code == KeyEvent.VK_LEFT) jumpSpeed = -4;
                if (code == KeyEvent.VK_RIGHT) jumpSpeed = 4;
                if (code == KeyEvent.VK_UP) isPunching = true;
                if (code == KeyEvent.VK_DOWN) isPunching = false;
            } 
            else if (internalEasterEgg == State.NAME_INPUT) { 
                if (code == KeyEvent.VK_LEFT) gameX -= 15;
                if (code == KeyEvent.VK_RIGHT) gameX += 15;
            } 
            else if (internalEasterEgg == State.OPTIONS) { 
                if (code == KeyEvent.VK_LEFT) gameX -= 10;
                if (code == KeyEvent.VK_RIGHT) gameX += 10;
                if (code == KeyEvent.VK_SPACE) {
                    isPunching = true;
                    if (Math.abs((gameX + 15) - (gameTargetX + 12)) < 30) {
                        gameScore += 100;
                        gameTargetX = rand.nextInt(400) + 150;
                    }
                }
            } 
            else if (internalEasterEgg == State.PLAYING) { 
                if ((code == KeyEvent.VK_SPACE || code == KeyEvent.VK_UP) && !gameJump) {
                    gameJump = true;
                    jumpSpeed = -12; 
                }
            }
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        int code = e.getKeyCode();
        if (code == KeyEvent.VK_W || code == KeyEvent.VK_UP) moveForward = false;
        if (code == KeyEvent.VK_S || code == KeyEvent.VK_DOWN) moveBackward = false;
        if (code == KeyEvent.VK_A || code == KeyEvent.VK_LEFT) rotateLeft = false;
        if (code == KeyEvent.VK_D || code == KeyEvent.VK_RIGHT) rotateRight = false;
        if (currentState == State.EASTER_EGG && code == KeyEvent.VK_SPACE) isPunching = false;
    }
    
    @Override public void keyTyped(KeyEvent e) {}
    @Override public void mouseClicked(MouseEvent e) {}
    @Override public void mouseReleased(MouseEvent e) {}
    @Override public void mouseEntered(MouseEvent e) {}
    @Override public void mouseExited(MouseEvent e) {}
    @Override public void mouseDragged(MouseEvent e) {}
}