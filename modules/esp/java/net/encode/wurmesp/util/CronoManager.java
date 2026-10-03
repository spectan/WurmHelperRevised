package net.encode.wurmesp.util;

public class CronoManager {
    private long timelapse;
    private long time;
    private long future;

    public CronoManager(long timelapse) {
        this.timelapse = timelapse;
        this.time = System.currentTimeMillis();
        this.future = this.time + this.timelapse;
    }

    public void restart(long timelapse) {
        this.timelapse = timelapse;
        this.time = System.currentTimeMillis();
        this.future = this.time + this.timelapse;
    }

    public boolean hasEnded() {
        return System.currentTimeMillis() > this.future;
    }
}
