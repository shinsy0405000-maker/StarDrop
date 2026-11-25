import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 소리: 음원 파일 없이 전부 계산해서 만든다 (웹 버전의 Web Audio 구성을 그대로 옮김).
 * 오디오 스레드 하나가 추진음과 효과음을 섞고 메아리(딜레이)를 입혀 스피커로 보낸다.
 * (예전에는 "웅" 하는 배경음도 깔았지만, 거슬린다는 의견이 있어 뺐다)
 */
final class Sound {
    private Sound() { }

    private static final float RATE = 44100f;
    private static final int CHUNK = 512;                       // 한 번에 섞는 샘플 수 (약 12ms)
    private static final List<float[]> QUEUE = new ArrayList<>(); // 새로 재생할 효과음
    private static volatile boolean muted, thrustOn, started, broken;

    static boolean isMuted() { return muted; }
    static void setMuted(boolean m) { muted = m; }
    static void thrust(boolean on) { thrustOn = on; }

    /** 첫 클릭이나 키 입력 때 오디오 스레드를 켠다 (웹처럼 사용자가 조작한 뒤에 소리를 시작) */
    static synchronized void start() {
        if (started || broken) return;
        started = true;
        Thread t = new Thread(Sound::run, "sound");
        t.setDaemon(true);
        t.start();
    }

    // ------------------------------------------------------------ 효과음 만들기
    /** 음 하나: f0 에서 f1 으로 미끄러지며, 빠르게 커졌다가 서서히 사라진다 */
    private static void tone(float[] buf, double f0, double f1, double dur, String type, double vol, double when) {
        int start = (int) (when * RATE), n = (int) (dur * RATE);
        double phase = 0;
        for (int i = 0; i < n && start + i < buf.length; i++) {
            double t = i / RATE;
            double f = f1 > 0 ? f0 * Math.pow(f1 / f0, t / dur) : f0;
            phase += f / RATE;
            double p = phase - Math.floor(phase), wave;
            if (type.equals("triangle")) wave = 4 * Math.abs(p - 0.5) - 1;
            else wave = Math.sin(2 * Math.PI * p);
            double env = t < 0.012 ? vol * t / 0.012 : vol * Math.pow(0.0008 / vol, (t - 0.012) / (dur - 0.012));
            buf[start + i] += (float) (wave * env);
        }
    }

    /** 잡음을 대역 필터로 걸러 "쉭" 소리를 만든다. 필터 중심 주파수가 f0 에서 f1 으로 바뀐다 */
    private static void noise(float[] buf, double dur, double f0, double f1, double vol) {
        int n = (int) (dur * RATE);
        Random r = new Random();
        Biquad bp = new Biquad();
        for (int i = 0; i < n && i < buf.length; i++) {
            double t = i / RATE;
            bp.bandpass(f0 * Math.pow(f1 / f0, t / dur), 1.1);
            double env = vol * Math.pow(0.0008 / vol, t / dur);
            buf[i] += (float) (bp.process(r.nextDouble() * 2 - 1) * env);
        }
    }

    private static float[] newBuffer(double seconds) { return new float[(int) (seconds * RATE)]; }

    private static void play(float[] buf) {
        if (muted || broken) return;
        start();
        synchronized (QUEUE) { QUEUE.add(buf); }
    }

    static void launch(double p) {
        float[] b = newBuffer(0.45);
        noise(b, 0.4, 300, 1500 + p * 1500, 0.35);
        tone(b, 140 + p * 80, 55, 0.35, "sine", 0.3, 0);
        play(b);
    }

    static void star(int n) {
        double f = new double[]{659.25, 830.61, 987.77}[Math.max(0, Math.min(2, n))];
        float[] b = newBuffer(0.65);
        tone(b, f, 0, 0.6, "sine", 0.22, 0);
        tone(b, f * 2, 0, 0.4, "triangle", 0.08, 0.04);
        play(b);
    }

    static void crash() {
        float[] b = newBuffer(0.75);
        noise(b, 0.7, 500, 50, 0.6);
        tone(b, 95, 32, 0.55, "sine", 0.5, 0);
        play(b);
    }

    static void win() {
        double[] notes = {523.25, 659.25, 783.99, 1046.5, 1318.5};
        float[] b = newBuffer(1.3);
        for (int i = 0; i < notes.length; i++) {
            tone(b, notes[i], 0, 0.8, "triangle", 0.17, i * 0.09);
            tone(b, notes[i] / 2, 0, 0.8, "sine", 0.1, i * 0.09);
        }
        play(b);
    }

    static void warp() {
        float[] b = newBuffer(0.4);
        tone(b, 260, 1300, 0.3, "sine", 0.2, 0);
        tone(b, 1300, 400, 0.3, "sine", 0.1, 0.05);
        play(b);
    }

    static void lost() {
        float[] b = newBuffer(0.75);
        tone(b, 420, 150, 0.7, "sine", 0.13, 0);
        play(b);
    }

    static void click() {
        float[] b = newBuffer(0.1);
        tone(b, 740, 0, 0.07, "triangle", 0.08, 0);
        play(b);
    }

    // ------------------------------------------------------------ 오디오 스레드
    private static void run() {
        try {
            AudioFormat fmt = new AudioFormat(RATE, 16, 1, true, false);
            javax.sound.sampled.SourceDataLine line = AudioSystem.getSourceDataLine(fmt);
            line.open(fmt, CHUNK * 2 * 4);
            line.start();
            byte[] out = new byte[CHUNK * 2];
            List<float[]> voices = new ArrayList<>();
            List<int[]> positions = new ArrayList<>();
            Random rnd = new Random();

            Biquad thrustFilter = new Biquad();
            thrustFilter.bandpass(700, 0.6);
            float[] delay = new float[(int) (0.27 * RATE)];      // 메아리: 0.27초 뒤에 34%씩 되돌아온다
            int delayPos = 0;
            double master = 0, thrustGain = 0;

            while (true) {
                synchronized (QUEUE) {
                    for (float[] q : QUEUE) { voices.add(q); positions.add(new int[1]); }
                    QUEUE.clear();
                }
                for (int i = 0; i < CHUNK; i++) {
                    // 효과음
                    double fx = 0;
                    for (int v = 0; v < voices.size(); v++) {
                        int[] pos = positions.get(v);
                        if (pos[0] < voices.get(v).length) fx += voices.get(v)[pos[0]++];
                    }
                    // 추진음
                    thrustGain += ((thrustOn ? 0.16 : 0) - thrustGain) * (1 / (0.04 * RATE));
                    double th = thrustFilter.process(rnd.nextDouble() * 2 - 1) * thrustGain;
                    // 메아리
                    double echoed = delay[delayPos];
                    delay[delayPos] = (float) (fx + echoed * 0.34);
                    delayPos = (delayPos + 1) % delay.length;
                    master += ((muted ? 0 : 0.7) - master) * (1 / (0.05 * RATE));
                    double s = Math.tanh((fx + th + echoed * 0.3) * master * 1.6);
                    short v = (short) (s * 30000);
                    out[i * 2] = (byte) v;
                    out[i * 2 + 1] = (byte) (v >> 8);
                }
                for (int v = voices.size() - 1; v >= 0; v--) {
                    if (positions.get(v)[0] >= voices.get(v).length) { voices.remove(v); positions.remove(v); }
                }
                line.write(out, 0, out.length);
            }
        } catch (Exception | Error e) {
            broken = true;          // 소리 장치가 없어도 게임은 계속된다
        }
    }

    /** 2차 필터 (웹 오디오의 BiquadFilterNode 와 같은 공식) */
    private static final class Biquad {
        private double b0, b1, b2, a1, a2, x1, x2, y1, y2;

        void lowpass(double f, double q) {
            double w = 2 * Math.PI * f / RATE, alpha = Math.sin(w) / (2 * q), c = Math.cos(w), a0 = 1 + alpha;
            b0 = (1 - c) / 2 / a0; b1 = (1 - c) / a0; b2 = b0; a1 = -2 * c / a0; a2 = (1 - alpha) / a0;
        }

        void bandpass(double f, double q) {
            double w = 2 * Math.PI * Math.min(f, RATE * 0.45) / RATE, alpha = Math.sin(w) / (2 * q), c = Math.cos(w), a0 = 1 + alpha;
            b0 = alpha / a0; b1 = 0; b2 = -alpha / a0; a1 = -2 * c / a0; a2 = (1 - alpha) / a0;
        }

        double process(double x) {
            double y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
            x2 = x1; x1 = x; y2 = y1; y1 = y;
            return y;
        }
    }
}
