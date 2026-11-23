import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.function.Supplier;

/**
 * 그림 한 장을 저장해 두는 캐시.
 *
 * 그리는 데 오래 걸리지만 자주 바뀌지 않는 그림(배경, 처음 화면 글자 등)을 위해 쓴다.
 * get() 에 "열쇠"(창 크기, 구역처럼 그림을 바꾸는 값들)를 함께 넘기면
 *   - 지난번과 열쇠가 같으면 저장해 둔 그림을 그대로 돌려주고
 *   - 열쇠가 하나라도 바뀌었으면 maker 로 새로 그려서 저장한다.
 */
final class CachedImage {
    private BufferedImage image;
    private Object[] key;

    BufferedImage get(Supplier<BufferedImage> maker, Object... key) {
        if (image == null || !Arrays.equals(this.key, key)) {
            image = maker.get();
            this.key = key;
        }
        return image;
    }

    /** 다음 get() 때 무조건 새로 그리게 한다 */
    void clear() {
        image = null;
        key = null;
    }
}
