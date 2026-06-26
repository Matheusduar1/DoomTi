package doomti;

import javax.swing.*;

public class DoomEngine extends JFrame {
    private GamePanel gamePanel;

    public DoomEngine() {
        setTitle("Raycasting Tech School - Doom Style");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);
        
        gamePanel = new GamePanel();
        add(gamePanel);
        pack();
        
        setLocationRelativeTo(null);
        setVisible(true);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new DoomEngine());
    }
}