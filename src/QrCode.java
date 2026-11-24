import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;

/**
 * 아주 작은 QR 코드 만들기 (라이브러리 없이).
 *
 * 후원 창에서 휴대폰 카메라로 찍을 QR을 그리려고 만들었다.
 * 글자를 바이트 그대로 담고(바이트 모드), 오류 정정은 M 단계(약 15% 손상까지 복구)로 고정이다.
 * 짧은 주소만 담으면 되니까 버전 1~10(가로세로 21~57칸)까지만 지원한다.
 *
 * 만드는 순서:
 *   1. 글자 → 비트로 바꾸고 빈자리를 채운다
 *   2. 리드-솔로몬 오류 정정 바이트를 붙인다
 *   3. 찾기 무늬(모서리 큰 네모), 타이밍 줄, 정렬 무늬를 그린다
 *   4. 데이터 비트를 지그재그로 채운다
 *   5. 8가지 마스크 중 가장 읽기 좋은 것을 골라 덮는다
 */
final class QrCode {
    /** 버전별 블록 하나에 붙는 오류 정정 바이트 수 (M 단계, 0번은 안 씀) */
    private static final int[] ECC_PER_BLOCK = {-1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26};
    /** 버전별 블록 개수 (M 단계) */
    private static final int[] BLOCKS = {-1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5};
    private static final int MAX_VERSION = 10;

    final int size;               // 한 변의 칸 수
    private final boolean[][] dark;      // [y][x] 검은 칸인가
    private final boolean[][] fixed;     // [y][x] 무늬 칸이라 데이터를 넣으면 안 되는가

    private QrCode(int version) {
        size = version * 4 + 17;
        dark = new boolean[size][size];
        fixed = new boolean[size][size];
    }

    /** 글자를 담은 QR 코드를 만든다. 너무 길면 IllegalArgumentException */
    static QrCode encode(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        int version = 1;
        while (true) {
            if (version > MAX_VERSION) throw new IllegalArgumentException("QR에 담기엔 너무 긴 글이에요");
            int countBits = version < 10 ? 8 : 16;
            if (4 + countBits + bytes.length * 8 <= dataCodewords(version) * 8) break;
            version++;
        }
        QrCode qr = new QrCode(version);
        qr.drawPatterns(version);
        qr.drawData(qr.addEcc(version, qr.dataBits(version, bytes)));
        qr.chooseMask(version);
        return qr;
    }

    boolean isDark(int x, int y) {
        return x >= 0 && y >= 0 && x < size && y < size && dark[y][x];
    }

    /** 칸 하나를 cell 픽셀로, 둘레에 흰 여백 4칸을 두고 그린 그림 */
    BufferedImage toImage(int cell, Color ink, Color paper) {
        int border = 4, px = (size + border * 2) * cell;
        BufferedImage im = new BufferedImage(px, px, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = im.createGraphics();
        g.setColor(paper);
        g.fillRect(0, 0, px, px);
        g.setColor(ink);
        for (int y = 0; y < size; y++)
            for (int x = 0; x < size; x++)
                if (dark[y][x]) g.fillRect((x + border) * cell, (y + border) * cell, cell, cell);
        g.dispose();
        return im;
    }

    // ------------------------------------------------------------ 1. 글자 → 데이터 바이트
    private byte[] dataBits(int version, byte[] bytes) {
        Bits b = new Bits();
        b.add(0b0100, 4);                                   // 바이트 모드
        b.add(bytes.length, version < 10 ? 8 : 16);          // 글자 수
        for (byte x : bytes) b.add(x & 0xFF, 8);
        int capacity = dataCodewords(version) * 8;
        b.add(0, Math.min(4, capacity - b.length));          // 끝 표시
        b.add(0, (8 - b.length % 8) % 8);                    // 바이트 경계 맞추기
        for (int pad = 0xEC; b.length < capacity; pad ^= 0xEC ^ 0x11) b.add(pad, 8);   // 남은 자리는 EC, 11 번갈아
        return b.toBytes();
    }

    /** 이 버전에 들어가는 전체 바이트 수 (데이터 + 오류 정정) */
    private static int rawCodewords(int v) {
        int n = (16 * v + 128) * v + 64;
        if (v >= 2) {
            int align = v / 7 + 2;
            n -= (25 * align - 10) * align - 55;
            if (v >= 7) n -= 36;
        }
        return n / 8;
    }

    private static int dataCodewords(int v) {
        return rawCodewords(v) - ECC_PER_BLOCK[v] * BLOCKS[v];
    }

    // ------------------------------------------------------------ 2. 오류 정정 바이트 붙이기
    private byte[] addEcc(int version, byte[] data) {
        int blocks = BLOCKS[version], ecc = ECC_PER_BLOCK[version], raw = rawCodewords(version);
        int shortBlocks = blocks - raw % blocks, shortLen = raw / blocks;   // 앞 블록들은 1바이트 짧다
        byte[] divisor = rsDivisor(ecc);
        byte[][] all = new byte[blocks][];
        for (int i = 0, k = 0; i < blocks; i++) {
            int len = shortLen - ecc + (i < shortBlocks ? 0 : 1);
            byte[] part = java.util.Arrays.copyOfRange(data, k, k + len);
            k += len;
            byte[] rem = rsRemainder(part, divisor);
            byte[] block = new byte[shortLen + 1];
            // 짧은 블록은 데이터 끝에 빈칸 1개를 두고 나중에 건너뛴다
            System.arraycopy(part, 0, block, 0, len);
            System.arraycopy(rem, 0, block, block.length - ecc, ecc);
            all[i] = block;
        }
        byte[] out = new byte[raw];
        int n = 0;
        for (int i = 0; i < shortLen + 1; i++)                // 블록들을 한 바이트씩 번갈아 섞는다
            for (int j = 0; j < blocks; j++)
                if (i != shortLen - ecc || j >= shortBlocks) out[n++] = all[j][i];
        return out;
    }

    /** 리드-솔로몬 생성 다항식 (계수만, 최고차항 1은 생략) */
    private static byte[] rsDivisor(int degree) {
        byte[] r = new byte[degree];
        r[degree - 1] = 1;
        int root = 1;
        for (int i = 0; i < degree; i++) {
            for (int j = 0; j < degree; j++) {
                r[j] = (byte) gfMul(r[j] & 0xFF, root);
                if (j + 1 < degree) r[j] ^= r[j + 1];
            }
            root = gfMul(root, 0x02);
        }
        return r;
    }

    private static byte[] rsRemainder(byte[] data, byte[] divisor) {
        byte[] r = new byte[divisor.length];
        for (byte b : data) {
            int factor = (b ^ r[0]) & 0xFF;
            System.arraycopy(r, 1, r, 0, r.length - 1);
            r[r.length - 1] = 0;
            for (int i = 0; i < r.length; i++) r[i] ^= (byte) gfMul(divisor[i] & 0xFF, factor);
        }
        return r;
    }

    /** GF(256) 곱셈 (QR 규칙: x^8 + x^4 + x^3 + x^2 + 1) */
    private static int gfMul(int x, int y) {
        int z = 0;
        for (int i = 7; i >= 0; i--) {
            z = (z << 1) ^ ((z >>> 7) * 0x11D);
            z ^= ((y >>> i) & 1) * x;
        }
        return z;
    }

    // ------------------------------------------------------------ 3. 정해진 무늬 그리기
    private void set(int x, int y, boolean isDark) {
        dark[y][x] = isDark;
        fixed[y][x] = true;
    }

    private void drawPatterns(int version) {
        for (int i = 0; i < size; i++) {                      // 타이밍 줄
            set(6, i, i % 2 == 0);
            set(i, 6, i % 2 == 0);
        }
        drawFinder(3, 3);
        drawFinder(size - 4, 3);
        drawFinder(3, size - 4);
        int[] pos = alignPositions(version);
        for (int i = 0; i < pos.length; i++)
            for (int j = 0; j < pos.length; j++) {
                boolean corner = (i == 0 && j == 0) || (i == 0 && j == pos.length - 1) || (i == pos.length - 1 && j == 0);
                if (!corner) drawAlign(pos[i], pos[j]);
            }
        drawFormat(0);                                        // 자리만 잡아 두고 마스크를 고른 뒤 다시 그린다
        drawVersion(version);
    }

    /** 모서리의 큰 네모 (둘레 흰 줄 포함) */
    private void drawFinder(int cx, int cy) {
        for (int dy = -4; dy <= 4; dy++)
            for (int dx = -4; dx <= 4; dx++) {
                int d = Math.max(Math.abs(dx), Math.abs(dy)), x = cx + dx, y = cy + dy;
                if (x >= 0 && y >= 0 && x < size && y < size) set(x, y, d != 2 && d != 4);
            }
    }

    private void drawAlign(int cx, int cy) {
        for (int dy = -2; dy <= 2; dy++)
            for (int dx = -2; dx <= 2; dx++) set(cx + dx, cy + dy, Math.max(Math.abs(dx), Math.abs(dy)) != 1);
    }

    private int[] alignPositions(int version) {
        if (version == 1) return new int[0];
        int n = version / 7 + 2;
        int step = (version * 4 + n * 2 + 1) / (n * 2 - 2) * 2;
        int[] r = new int[n];
        r[0] = 6;
        for (int i = n - 1, p = size - 7; i >= 1; i--, p -= step) r[i] = p;
        return r;
    }

    /** 형식 정보 (오류 정정 단계 M + 마스크 번호). 두 군데에 똑같이 그린다 */
    private void drawFormat(int mask) {
        int data = (0 << 3) | mask;                           // M 단계의 번호는 0
        int rem = data;
        for (int i = 0; i < 10; i++) rem = (rem << 1) ^ ((rem >>> 9) * 0x537);
        int bits = (data << 10 | rem) ^ 0x5412;
        for (int i = 0; i <= 5; i++) set(8, i, bit(bits, i));
        set(8, 7, bit(bits, 6));
        set(8, 8, bit(bits, 7));
        set(7, 8, bit(bits, 8));
        for (int i = 9; i < 15; i++) set(14 - i, 8, bit(bits, i));
        for (int i = 0; i < 8; i++) set(size - 1 - i, 8, bit(bits, i));
        for (int i = 8; i < 15; i++) set(8, size - 15 + i, bit(bits, i));
        set(8, size - 8, true);                               // 항상 검은 칸 하나
    }

    /** 버전 7 이상만 쓰는 버전 정보 */
    private void drawVersion(int version) {
        if (version < 7) return;
        int rem = version;
        for (int i = 0; i < 12; i++) rem = (rem << 1) ^ ((rem >>> 11) * 0x1F25);
        int bits = version << 12 | rem;
        for (int i = 0; i < 18; i++) {
            boolean b = bit(bits, i);
            int a = size - 11 + i % 3, c = i / 3;
            set(a, c, b);
            set(c, a, b);
        }
    }

    // ------------------------------------------------------------ 4. 데이터 채우기
    private void drawData(byte[] data) {
        int i = 0;
        for (int right = size - 1; right >= 1; right -= 2) {     // 오른쪽부터 두 줄씩
            if (right == 6) right = 5;                           // 세로 타이밍 줄은 건너뛴다
            for (int vert = 0; vert < size; vert++)
                for (int j = 0; j < 2; j++) {
                    int x = right - j;
                    boolean upward = ((right + 1) & 2) == 0;
                    int y = upward ? size - 1 - vert : vert;
                    if (!fixed[y][x] && i < data.length * 8) {
                        dark[y][x] = bit(data[i >>> 3], 7 - (i & 7));
                        i++;
                    }
                }
        }
    }

    // ------------------------------------------------------------ 5. 마스크 고르기
    private void applyMask(int mask) {
        for (int y = 0; y < size; y++)
            for (int x = 0; x < size; x++) {
                if (fixed[y][x]) continue;
                boolean flip = switch (mask) {
                    case 0 -> (x + y) % 2 == 0;
                    case 1 -> y % 2 == 0;
                    case 2 -> x % 3 == 0;
                    case 3 -> (x + y) % 3 == 0;
                    case 4 -> (x / 3 + y / 2) % 2 == 0;
                    case 5 -> x * y % 2 + x * y % 3 == 0;
                    case 6 -> (x * y % 2 + x * y % 3) % 2 == 0;
                    default -> ((x + y) % 2 + x * y % 3) % 2 == 0;
                };
                dark[y][x] ^= flip;
            }
    }

    private void chooseMask(int version) {
        int best = 0;
        long bestScore = Long.MAX_VALUE;
        for (int m = 0; m < 8; m++) {
            applyMask(m);
            drawFormat(m);
            long score = penalty();
            if (score < bestScore) {
                bestScore = score;
                best = m;
            }
            applyMask(m);                                    // 같은 마스크를 한 번 더 덮으면 원래대로 돌아온다
        }
        applyMask(best);
        drawFormat(best);
    }

    /** 읽기 나쁜 모양일수록 큰 점수 (같은 색이 길게 이어짐, 2x2 덩어리, 흑백 비율) */
    private long penalty() {
        long score = 0;
        int darkCount = 0;
        for (int a = 0; a < size; a++) {
            int runRow = 1, runCol = 1;
            for (int b = 1; b < size; b++) {
                if (dark[a][b] == dark[a][b - 1]) runRow++;
                else runRow = 1;
                if (runRow == 5) score += 3;
                else if (runRow > 5) score++;
                if (dark[b][a] == dark[b - 1][a]) runCol++;
                else runCol = 1;
                if (runCol == 5) score += 3;
                else if (runCol > 5) score++;
            }
        }
        for (int y = 0; y < size; y++)
            for (int x = 0; x < size; x++) {
                if (dark[y][x]) darkCount++;
                if (x + 1 < size && y + 1 < size) {
                    boolean c = dark[y][x];
                    if (c == dark[y][x + 1] && c == dark[y + 1][x] && c == dark[y + 1][x + 1]) score += 3;
                }
            }
        int total = size * size;
        int k = (Math.abs(darkCount * 20 - total * 10) + total - 1) / total - 1;   // 50%에서 5%씩 벗어날 때마다
        return score + Math.max(0, k) * 10L;
    }

    private static boolean bit(int x, int i) {
        return ((x >>> i) & 1) != 0;
    }

    /** 비트를 차례로 모으는 작은 통 */
    private static final class Bits {
        private final java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        private int cur, length;

        void add(int value, int count) {
            for (int i = count - 1; i >= 0; i--) {
                cur = (cur << 1) | ((value >>> i) & 1);
                length++;
                if (length % 8 == 0) {
                    out.write(cur);
                    cur = 0;
                }
            }
        }

        byte[] toBytes() {
            return out.toByteArray();
        }
    }
}
