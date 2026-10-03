package com.wurmonline.client.renderer.gui;

import com.wurmonline.client.renderer.Matrix;
import com.wurmonline.client.renderer.backend.Primitive;
import com.wurmonline.client.renderer.backend.Queue;
import com.wurmonline.client.renderer.backend.VertexBuffer;
import java.nio.FloatBuffer;

final class EspWCheckBox
extends FlexComponent {
    private String label;
    boolean checked = false;
    boolean enabled = true;
    private float customR = 1.0f;
    private float customG = 1.0f;
    private float customB = 1.0f;
    private static final VertexBuffer vbo = VertexBuffer.create((VertexBuffer.Usage)VertexBuffer.Usage.GUI, (int)12, (boolean)true, (boolean)false, (boolean)false, (boolean)false, (boolean)false, (int)0, (int)0, (boolean)false, (boolean)true);
    private final Matrix modelMatrix;
    private CheckBoxListener checkboxListener;

    EspWCheckBox(String label, CheckBoxListener checkboxListener) {
        super("Checkbox " + label);
        this.height = this.text.getHeight() + 1;
        this.setLabel(label);
        this.modelMatrix = Matrix.createIdentity();
        this.checkboxListener = checkboxListener;
    }

    public void setLabel(String newLabel) {
        this.label = newLabel;
        this.setSize(this.text.getWidth(this.label) + 16, this.height);
    }

    protected void renderComponent(Queue queue, float alpha) {
        float colR = 0.8f;
        float colG = 0.8f;
        float colB = 0.8f;
        if (this.enabled) {
            colR = this.customR;
            colG = this.customG;
            colB = this.customB;
        }
        Primitive prim = queue.reservePrimitive();
        prim.type = Primitive.Type.LINES;
        prim.num = this.checked ? 6 : 4;
        prim.r = colR;
        prim.g = colG;
        prim.b = colB;
        prim.a = 1.0f;
        prim.texture[1] = null;
        prim.texture[0] = null;
        prim.texenv[0] = Primitive.TexEnv.MODULATE;
        prim.vertex = vbo;
        prim.index = null;
        prim.clipRect = HeadsUpDisplay.scissor.getCurrent();
        int dy = (this.height - 8) / 2;
        this.modelMatrix.setTranslation((float)this.x, (float)(this.y + dy), 0.0f);
        queue.queue(prim, this.modelMatrix);
        this.text.moveTo(this.x + this.height, this.y + this.text.getHeight());
        this.text.paint(queue, this.label, colR, colG, colB, 1.0f);
    }

    protected void leftPressed(int xMouse, int yMouse, int clickCount) {
        if (this.enabled && xMouse <= this.x + 16 && xMouse >= this.x && yMouse >= this.y && yMouse <= this.y + this.height) {
            this.checked = !this.checked;
            this.checkboxListener.checkboxClicked(this);
        }
    }

    protected int getMouseCursor(int x, int y) {
        if (this.enabled && x <= this.x + 16 && x >= this.x && y >= this.y && y <= this.y + this.height) {
            return 1;
        }
        return super.getMouseCursor(x, y);
    }

    static {
        FloatBuffer vertex = vbo.lock();
        vertex.put(4.0f);
        vertex.put(0.0f);
        vertex.put(0.0f);
        vertex.put(13.0f);
        vertex.put(0.0f);
        vertex.put(0.0f);
        vertex.put(3.0f);
        vertex.put(8.0f);
        vertex.put(0.0f);
        vertex.put(13.0f);
        vertex.put(8.0f);
        vertex.put(0.0f);
        vertex.put(4.0f);
        vertex.put(0.0f);
        vertex.put(0.0f);
        vertex.put(4.0f);
        vertex.put(8.0f);
        vertex.put(0.0f);
        vertex.put(13.0f);
        vertex.put(0.0f);
        vertex.put(0.0f);
        vertex.put(13.0f);
        vertex.put(8.0f);
        vertex.put(0.0f);
        vertex.put(6.0f);
        vertex.put(2.0f);
        vertex.put(0.0f);
        vertex.put(11.0f);
        vertex.put(7.0f);
        vertex.put(0.0f);
        vertex.put(11.0f);
        vertex.put(2.0f);
        vertex.put(0.0f);
        vertex.put(6.0f);
        vertex.put(7.0f);
        vertex.put(0.0f);
        vbo.unlock();
    }
}

