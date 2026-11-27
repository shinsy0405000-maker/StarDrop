import javax.swing.SwingUtilities;

import java.awt.GraphicsEnvironment;
import java.util.concurrent.locks.LockSupport;

/** 게임 루프: 정해진 간격마다 frame(계산 한 번 + 그리기 한 번)을 부른다 */
final class GameLoop {
    private GameLoop() { }

    /**
     * 모니터 주사율(60Hz, 144Hz 등)에 맞춰 "계산 → 그리기"를 반복한다.
     * Swing 의 Timer 는 윈도우에서 간격이 들쭉날쭉해서 움직임이 끊겨 보이므로 직접 만든다.
     */
    static void start(Runnable frame) {
        Thread precise = new Thread(() -> {     // 윈도우 타이머를 1ms 단위로 정밀하게 만드는 요령
            try { Thread.sleep(Long.MAX_VALUE); } catch (InterruptedException e) { /* 종료 */ }
        });
        precise.setDaemon(true);
        precise.start();

        int hz = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDisplayMode().getRefreshRate();
        if (hz < 30 || hz > 240) hz = 60;
        final long frameNanos = 1_000_000_000L / hz;
        Thread loop = new Thread(() -> {
            long next = System.nanoTime();
            while (true) {
                try {
                    SwingUtilities.invokeAndWait(frame);
                } catch (Exception e) {
                    return;
                }
                next += frameNanos;
                long sleep = next - System.nanoTime();
                if (sleep > 0) LockSupport.parkNanos(sleep); else next = System.nanoTime();
            }
        }, "game-loop");
        loop.setDaemon(true);
        loop.start();
    }
}
