package doomti;

import java.awt.image.BufferedImage;

public class Entity {
    public double x, y;
    public int type;      // 3 = Professor, 5 = PC
    public double vScale;  // Escala vertical do sprite
    public double vOffset; // Altura em relação ao chão
    public BufferedImage tex;
    
    public Entity(double x, double y, int type, double vScale, double vOffset, BufferedImage tex) {
        this.x = x;
        this.y = y;
        this.type = type;
        this.vScale = vScale;
        this.vOffset = vOffset;
        this.tex = tex;
    }
}