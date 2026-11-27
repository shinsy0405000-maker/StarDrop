import java.util.HashMap;
import java.util.Map;

/**
 * 번역 표: 한국어 글 → {English, 日本語, 中文}.
 * 새 글을 화면에 넣을 때는 여기에 add(한국어, 영어, 일본어, 중국어) 한 줄을 더하고, 코드에서는 Lang.t("한국어") 로 쓴다.
 * 숫자가 들어가는 글(%d, %s 등)은 네 언어의 빈칸 개수와 순서가 같아야 한다.
 */
final class LangTable {
    private LangTable() { }

    private static Map<String, String[]> table;

    private static void add(String ko, String en, String ja, String zh) {
        table.put(ko, new String[]{en, ja, zh});
    }

    /** 표를 만들어 돌려준다 (Lang 이 처음 쓰일 때 한 번) */
    static Map<String, String[]> build() {
        table = new HashMap<>();
        // 게임 이름과 처음 화면
        add("별똥 택배", "StarDrop", "流れ星宅配便", "流星快递");
        add("별똥", "STAR", "流れ星", "流星");
        add("택배", "DROP", "宅配便", "快递");
        add("엔진은 1.2초치뿐입니다. 행성의 중력을 빌려 소포를 휘돌려 보내세요. "
                + "지구에서 태양까지 9단계, 144개 구역은 전부 자동 풀이기가 검증한 항로가 있고, 그 뒤로는 무한히 새 구역이 만들어집니다.",
            "Your engine has only 1.2 seconds of fuel. Borrow the planets' gravity to swing your parcel to its destination. "
                + "From Earth to the Sun there are 9 stages and 144 zones, each with a route checked by the auto-solver, and after that new zones are made forever.",
            "エンジンは1.2秒分だけ。惑星の重力を借りて、小包を振り回して届けましょう。"
                + "地球から太陽まで9ステージ・144区域すべてに自動ソルバーが確かめた航路があり、その先は新しい区域が無限に作られます。",
            "引擎只能工作1.2秒。借助行星的引力，把包裹甩向目的地。"
                + "从地球到太阳共9个阶段、144个区域，每个都有自动求解器验证过的航线，之后还会无限生成新区域。");
        add("취급주의 · 블랙홀 근처 배송", "FRAGILE · DELIVERY NEAR BLACK HOLES", "取扱注意 · ブラックホール付近配送", "小心轻放 · 黑洞附近配送");
        add("배달 시작", "Start Delivery", "配達開始", "开始配送");
        add("구역 목록", "Zones", "区域一覧", "区域列表");
        add("무한 배달", "Endless", "無限配達", "无限配送");
        add("후원하기", "Donate", "寄付する", "赞助");
        add("버그 제보", "Report Bug", "バグ報告", "报告错误");
        add("언어", "Language", "言語", "语言");
        add("마우스", "Mouse", "マウス", "鼠标");
        add("조준", "Aim", "照準", "瞄准");
        add("클릭", "Click", "クリック", "点击");
        add("발사", "Launch", "発射", "发射");
        add("추진", "Thrust", "推進", "推进");
        add("미세 조정 (Shift로 더 세밀하게)", "Fine-tune (Shift for finer)", "微調整（Shiftでさらに細かく）", "微调（按住Shift更精细）");

        // 게임 화면 (HUD)
        add("단계", "Stage", "ステージ", "阶段");
        add("운송장", "Waybill", "送り状", "运单");
        add("구역", "Zone", "区域", "区域");
        add("별", "Stars", "星", "星星");
        add("%d회", "%d", "%d回", "%d次");
        add("다시 조준", "Re-aim", "照準し直す", "重新瞄准");
        add("정답 방향 보기", "Show Hint", "正解の方向", "显示答案方向");
        add("소리 켜기", "Sound On", "音をオン", "打开声音");
        add("소리 끄기", "Sound Off", "音をオフ", "关闭声音");
        add("각도 %.1f° · 세기 %d%%", "Angle %.1f° · Power %d%%", "角度 %.1f° · 強さ %d%%", "角度 %.1f° · 力度 %d%%");
        add("추진 연료", "Fuel", "推進燃料", "推进燃料");
        add("삼켜졌습니다", "Swallowed", "飲み込まれた", "被吞噬了");
        add("파손", "Wrecked", "破損", "损坏");
        add("연락 두절", "Signal lost", "通信途絶", "失去联系");
        add("우주 미아", "Lost in space", "宇宙の迷子", "迷失太空");
        add("항로 계산 중", "PLOTTING ROUTE", "航路を計算中", "正在计算航线");

        // 구역 목록
        add("배달 구역", "Delivery Zones", "配達区域", "配送区域");
        add("모은 별 %d / %d", "Stars %d / %d", "集めた星 %d / %d", "已收集星星 %d / %d");
        add("잠김", "Locked", "ロック中", "未解锁");
        add("미배달", "Not delivered", "未配達", "未配送");
        add("처음 화면", "Title", "タイトル", "主界面");

        // 배달 완료 영수증
        add("배달 완료", "DELIVERED", "配達完了", "配送完成");
        add("별 세 개를 모두 지났습니다.", "You passed all three stars.", "3つの星をすべて通過しました。", "三颗星星全部经过了。");
        add("별 %d개를 놓쳤습니다. 별은 세 개를 모두 지나는 궤도 위에 놓여 있습니다.",
            "You missed %d star(s). There is an orbit that passes through all three.",
            "星を%d個逃しました。星は3つすべてを通る軌道の上に置かれています。",
            "错过了%d颗星星。总有一条轨道能经过全部三颗星星。");
        add("무한 배달 No. %d", "Endless No. %d", "無限配達 No. %d", "无限配送 No. %d");
        add("운송장 %s No. %02d · %s", "Waybill %s No. %02d · %s", "送り状 %s No. %02d · %s", "运单 %s No. %02d · %s");
        add("%.2f초", "%.2f s", "%.2f秒", "%.2f秒");
        add("비행 시간", "Flight time", "飛行時間", "飞行时间");
        add("발사 횟수", "Launches", "発射回数", "发射次数");
        add("무한 배달 시작", "Start Endless", "無限配達を始める", "开始无限配送");
        add("다음 배달", "Next Delivery", "次の配達", "下一次配送");
        add("다음 단계: %s", "Next Stage: %s", "次のステージ: %s", "下一阶段：%s");
        add("다음 구역", "Next Zone", "次の区域", "下一区域");
        add("다시 배달", "Retry", "もう一度", "重新配送");

        // 단계 (태양계)
        add("지구", "Earth", "地球", "地球");
        add("화성", "Mars", "火星", "火星");
        add("금성", "Venus", "金星", "金星");
        add("수성", "Mercury", "水星", "水星");
        add("목성", "Jupiter", "木星", "木星");
        add("토성", "Saturn", "土星", "土星");
        add("천왕성", "Uranus", "天王星", "天王星");
        add("해왕성", "Neptune", "海王星", "海王星");
        add("태양", "Sun", "太陽", "太阳");

        // 구역 이름 16개
        add("첫 배달", "First Delivery", "初めての配達", "首次配送");
        add("돌아가는 길", "The Long Way Round", "回り道", "绕远路");
        add("달그림자", "Moon Shadow", "月の影", "月影");
        add("세 개의 우물", "Three Wells", "三つの井戸", "三口井");
        add("공전 시간표", "Orbit Timetable", "公転の時刻表", "公转时刻表");
        add("검은 입", "Black Mouth", "黒い口", "黑色巨口");
        add("밀어내는 별", "Pushing Star", "押し返す星", "推开的星");
        add("지름길", "Shortcut", "近道", "捷径");
        add("사건의 지평선", "Event Horizon", "事象の地平線", "事件视界");
        add("역풍", "Headwind", "逆風", "逆风");
        add("환승역", "Transfer Station", "乗り換え駅", "换乘站");
        add("당기고 밀고", "Push and Pull", "引いて押して", "一拉一推");
        add("쌍둥이 달", "Twin Moons", "双子の月", "双子月");
        add("위험한 지름길", "Risky Shortcut", "危険な近道", "危险的捷径");
        add("혼잡 구간", "Congestion", "混雑区間", "拥堵路段");
        add("마지막 소포", "The Last Parcel", "最後の小包", "最后的包裹");

        // 구역 이름: 화성 ~ 태양 (Campaign.NAMES)
        // 화성
        add("붉은 모래바람", "Red Dust Storm", "赤い砂嵐", "红色沙尘暴");
        add("협곡 우회로", "Canyon Detour", "峡谷の回り道", "峡谷绕行");
        add("포보스 그림자", "Phobos Shadow", "フォボスの影", "火卫一之影");
        add("세 개의 분화구", "Three Craters", "三つのクレーター", "三座陨石坑");
        add("데이모스 시간표", "Deimos Timetable", "ダイモスの時刻表", "火卫二时刻表");
        add("녹슨 구멍", "Rusty Hole", "錆びた穴", "锈色深洞");
        add("먼지 회오리", "Dust Devil", "塵旋風", "尘卷风");
        add("계곡 지름길", "Valley Shortcut", "谷の近道", "峡谷捷径");
        add("붉은 지평선", "Red Horizon", "赤い地平線", "红色地平线");
        add("극관 바람", "Polar Wind", "極冠の風", "极冠之风");
        add("기지 환승역", "Base Transfer", "基地の乗換駅", "基地换乘站");
        add("두 달의 줄다리기", "Moon Tug-of-War", "二つの月の綱引き", "双月拔河");
        add("포보스와 데이모스", "Phobos & Deimos", "フォボスとダイモス", "火卫一与火卫二");
        add("낭떠러지 지름길", "Cliffside Shortcut", "崖っぷちの近道", "悬崖捷径");
        add("착륙선 정체", "Lander Traffic", "着陸船の渋滞", "着陆舱拥堵");
        add("올림푸스 정상", "Olympus Summit", "オリンポスの頂", "奥林匹斯之巅");
        // 금성
        add("노란 구름 속", "Into Yellow Clouds", "黄色い雲の中", "黄云之中");
        add("산성비 우회", "Acid Rain Detour", "酸性雨の回り道", "绕开酸雨");
        add("새벽별 그림자", "Morning Star Shade", "明けの明星の影", "晨星之影");
        add("세 개의 화산", "Three Volcanoes", "三つの火山", "三座火山");
        add("거꾸로 도는 하루", "Backward Day", "逆回りの一日", "逆转的一天");
        add("압력 함정", "Pressure Trap", "気圧の罠", "气压陷阱");
        add("뜨거운 바람", "Hot Wind", "熱風", "热风");
        add("구름 터널", "Cloud Tunnel", "雲のトンネル", "云中隧道");
        add("끓는 지평선", "Boiling Horizon", "沸く地平線", "沸腾地平线");
        add("초회전 바람", "Super-Rotation", "超回転の風", "超级自转风");
        add("구름 정거장", "Cloud Station", "雲の停留所", "云端驿站");
        add("밀고 당기는 대기", "Tug of Air", "押し引く大気", "推拉的大气");
        add("쌍둥이 번개", "Twin Lightning", "双子の稲妻", "双子闪电");
        add("황산 지름길", "Acid Shortcut", "硫酸の近道", "硫酸捷径");
        add("짙은 안개 구간", "Thick Fog Zone", "濃霧区間", "浓雾路段");
        add("금성의 문", "Venus Gate", "金星の門", "金星之门");
        // 수성
        add("태양 곁 배달", "Sunside Delivery", "太陽のそばへ", "太阳旁的配送");
        add("그늘진 길", "Shady Path", "日陰の道", "背阴小路");
        add("칼로리스 그림자", "Caloris Shadow", "カロリスの影", "卡洛里斯之影");
        add("세 개의 구덩이", "Three Pits", "三つの穴", "三个深坑");
        add("빠른 공전", "Fast Orbit", "速い公転", "快速公转");
        add("깊은 우물", "Deep Well", "深い井戸", "深井");
        add("태양풍", "Solar Wind", "太陽風", "太阳风");
        add("낮과 밤 사이", "Day and Night", "昼と夜の間", "昼夜之间");
        add("타오르는 경계", "Burning Edge", "燃える境界", "燃烧边界");
        add("열기 역풍", "Heat Headwind", "熱の逆風", "热浪逆风");
        add("그늘 환승역", "Shade Station", "日陰の乗換駅", "阴影换乘站");
        add("두 개의 소용돌이", "Two Whirlpools", "二つの渦", "两个漩涡");
        add("쌍둥이 구멍", "Twin Holes", "双子の穴", "双子黑洞");
        add("녹는 지름길", "Melting Shortcut", "溶ける近道", "熔化的捷径");
        add("근일점 혼잡", "Perihelion Rush", "近日点の混雑", "近日点拥堵");
        add("수성의 끝", "Mercury's End", "水星の果て", "水星尽头");
        // 목성
        add("거인의 품", "Giant's Embrace", "巨人の懐", "巨人怀抱");
        add("줄무늬 우회로", "Striped Detour", "縞模様の回り道", "条纹绕行");
        add("이오의 그림자", "Io's Shadow", "イオの影", "木卫一之影");
        add("네 개의 우물", "Four Wells", "四つの井戸", "四口井");
        add("갈릴레이 시계", "Galileo's Clock", "ガリレオの時計", "伽利略时钟");
        add("대적점", "Great Red Spot", "大赤斑", "大红斑");
        add("유로파 반발", "Europa Rebound", "エウロパの反発", "木卫二反弹");
        add("가니메데 지름길", "Ganymede Shortcut", "ガニメデの近道", "木卫三捷径");
        add("폭풍의 눈", "Eye of the Storm", "嵐の目", "风暴之眼");
        add("제트기류", "Jet Stream", "ジェット気流", "急流");
        add("칼리스토 환승역", "Callisto Station", "カリストの乗換駅", "木卫四换乘站");
        add("거인의 줄다리기", "Giant Tug-of-War", "巨人の綱引き", "巨人拔河");
        add("달 행렬", "Moon Parade", "月の行列", "卫星巡游");
        add("고리 속 지름길", "Ring Shortcut", "リングの近道", "光环捷径");
        add("위성 러시아워", "Moon Rush Hour", "衛星ラッシュ", "卫星高峰期");
        add("목성의 왕관", "Jupiter's Crown", "木星の王冠", "木星之冠");
        // 토성
        add("고리 입구", "Ring Gate", "環の入り口", "光环入口");
        add("고리 둘레길", "Around the Rings", "環のまわり道", "环绕光环");
        add("타이탄 그림자", "Titan's Shadow", "タイタンの影", "土卫六之影");
        add("세 개의 고리", "Three Rings", "三つの環", "三道光环");
        add("엔셀라두스 시계", "Enceladus Clock", "エンケラドスの時計", "土卫二时钟");
        add("육각 폭풍", "Hexagon Storm", "六角形の嵐", "六边形风暴");
        add("얼음 튕김", "Ice Bounce", "氷の跳ね返り", "冰面反弹");
        add("고리 틈 지름길", "Gap Shortcut", "隙間の近道", "缝隙捷径");
        add("얼음 지평선", "Icy Horizon", "氷の地平線", "冰之地平线");
        add("고리 바람", "Ring Wind", "環の風", "光环之风");
        add("미마스 환승역", "Mimas Station", "ミマスの乗換駅", "土卫一换乘站");
        add("얼음과 불꽃", "Ice and Fire", "氷と炎", "冰与火");
        add("양치기 달", "Shepherd Moons", "羊飼い衛星", "牧羊犬卫星");
        add("얼음 조각 사이", "Between Ice Shards", "氷のかけらの間", "冰屑之间");
        add("고리 정체", "Ring Traffic", "環の渋滞", "光环拥堵");
        add("카시니 틈", "Cassini Division", "カッシーニの間隙", "卡西尼缝");
        // 천왕성
        add("누운 행성", "Sideways Planet", "横倒しの惑星", "横躺的行星");
        add("기울어진 길", "Tilted Road", "傾いた道", "倾斜之路");
        add("미란다 그림자", "Miranda's Shadow", "ミランダの影", "天卫五之影");
        add("푸른 우물 셋", "Three Blue Wells", "三つの青い井戸", "三口蓝井");
        add("42년의 낮", "42-Year Day", "42年の昼", "42年的白昼");
        add("차가운 구멍", "Cold Hole", "冷たい穴", "寒冷深洞");
        add("옆바람", "Crosswind", "横風", "侧风");
        add("극지 통로", "Polar Passage", "極地の通路", "极地通道");
        add("얼어붙은 지평선", "Frozen Horizon", "凍った地平線", "冰封地平线");
        add("메탄 역풍", "Methane Headwind", "メタンの逆風", "甲烷逆风");
        add("티타니아 환승역", "Titania Station", "ティタニアの乗換駅", "天卫三换乘站");
        add("기울어진 줄다리기", "Tilted Tug-of-War", "傾いた綱引き", "倾斜拔河");
        add("쌍둥이 얼음달", "Twin Ice Moons", "双子の氷の月", "双子冰卫星");
        add("뒤집힌 지름길", "Flipped Shortcut", "逆さの近道", "颠倒的捷径");
        add("자기장 혼잡", "Magnetic Jam", "磁場の渋滞", "磁场拥堵");
        add("천왕성의 고리", "Uranus Rings", "天王星の環", "天王星光环");
        // 해왕성
        add("푸른 끝자락", "Blue Frontier", "青い果て", "蓝色边疆");
        add("먼 길", "The Far Road", "遠い道", "漫长远路");
        add("트리톤 그림자", "Triton's Shadow", "トリトンの影", "海卫一之影");
        add("깊은 바다 셋", "Three Deep Seas", "三つの深海", "三片深海");
        add("165년의 공전", "165-Year Orbit", "165年の公転", "165年公转");
        add("대암점", "Great Dark Spot", "大暗斑", "大黑斑");
        add("초음속 바람", "Supersonic Wind", "超音速の風", "超音速风");
        add("얼음 화산 통로", "Geyser Passage", "氷火山の通路", "冰火山通道");
        add("폭풍 지평선", "Storm Horizon", "嵐の地平線", "风暴地平线");
        add("거꾸로 부는 바람", "Retrograde Wind", "逆行する風", "逆行之风");
        add("네레이드 환승역", "Nereid Station", "ネレイドの乗換駅", "海卫二换乘站");
        add("바람과 구멍", "Wind and Void", "風と穴", "风与虚空");
        add("쌍둥이 폭풍", "Twin Storms", "双子の嵐", "双子风暴");
        add("다이아몬드 비", "Diamond Rain", "ダイヤモンドの雨", "钻石雨");
        add("폭풍 정체", "Storm Jam", "嵐の渋滞", "风暴拥堵");
        add("태양계 끝 우체국", "Edge Post Office", "果ての郵便局", "边境邮局");
        // 태양
        add("코로나 진입", "Into the Corona", "コロナ突入", "进入日冕");
        add("플레어 우회로", "Flare Detour", "フレアの回り道", "绕开耀斑");
        add("흑점 그늘", "Sunspot Shade", "黒点の陰", "黑子阴影");
        add("세 개의 불기둥", "Three Pillars", "三本の火柱", "三根火柱");
        add("자전 시간표", "Spin Timetable", "自転の時刻表", "自转时刻表");
        add("흑점 지대", "Sunspot Zone", "黒点地帯", "黑子地带");
        add("홍염", "Prominence", "プロミネンス", "日珥");
        add("자기 고리 지름길", "Loop Shortcut", "磁気ループの近道", "磁环捷径");
        add("광구의 지평선", "Photosphere Edge", "光球の地平線", "光球地平线");
        add("정면 태양풍", "Solar Gale", "太陽風の正面", "正面太阳风");
        add("핵융합 환승역", "Fusion Station", "核融合の乗換駅", "核聚变换乘站");
        add("불과 어둠", "Fire and Dark", "炎と闇", "火与暗");
        add("쌍둥이 플레어", "Twin Flares", "双子のフレア", "双子耀斑");
        add("불꽃 지름길", "Blazing Shortcut", "炎の近道", "烈焰捷径");
        add("코로나 폭풍", "Coronal Storm", "コロナ嵐", "日冕风暴");
        add("마지막 불꽃", "The Final Flame", "最後の炎", "最后的火焰");

        // 안내 문구 (지구 단계)
        add("마우스를 움직여 방향과 세기를 정하고, 클릭하면 발사합니다. 점선은 궤도의 앞부분만 보여 줍니다.",
            "Move the mouse to set direction and power, then click to launch. The dotted line shows only the start of the orbit.",
            "マウスを動かして方向と強さを決め、クリックで発射します。点線は軌道の最初の部分だけを示します。",
            "移动鼠标决定方向和力度，点击发射。虚线只显示轨道的前一段。");
        add("날아가는 동안 화면을 누르고 있거나 Space를 누르면 진행 방향으로 추진합니다. 연료는 1.2초 분량입니다.",
            "While flying, hold the mouse button or Space to thrust forward. You have 1.2 seconds of fuel.",
            "飛んでいる間に画面を押し続けるかSpaceを押すと、進行方向に推進します。燃料は1.2秒分です。",
            "飞行时按住画面或按Space，就会朝前进方向推进。燃料只够1.2秒。");
        add("달은 발사하는 순간부터 공전합니다. 점선 예측에는 달의 움직임이 이미 반영되어 있습니다.",
            "Moons start orbiting the moment you launch. The dotted prediction already includes their motion.",
            "月は発射した瞬間から公転します。点線の予測には月の動きがすでに反映されています。",
            "月球从发射那一刻开始公转。虚线预测已经考虑了月球的运动。");
        add("블랙홀은 작지만 끌어당기는 힘이 큽니다. 주황색 원반 안쪽으로 들어가면 돌아오지 못합니다.",
            "Black holes are small but pull hard. Cross inside the orange disk and there's no coming back.",
            "ブラックホールは小さいですが、引き寄せる力が強いです。オレンジ色の円盤の内側に入ると戻れません。",
            "黑洞虽小，引力却很强。一旦进入橙色圆盘内侧就回不来了。");
        add("청록색 별은 소포를 밀어냅니다. 부딪히지는 않으니 방향을 꺾는 데 쓰세요.",
            "Teal stars push your parcel away. You can't crash into them, so use them to bend your course.",
            "青緑色の星は小包を押し返します。ぶつかることはないので、向きを変えるのに使いましょう。",
            "青绿色的星星会把包裹推开。不会撞上，可以用来改变方向。");
        add("보라색 웜홀에 들어가면 속도를 그대로 지닌 채 반대편 웜홀에서 나옵니다.",
            "Enter the purple wormhole and you'll come out of the other one at the same speed.",
            "紫色のワームホールに入ると、速度を保ったまま反対側のワームホールから出てきます。",
            "进入紫色虫洞后，会保持原速度从另一端的虫洞出来。");

        // 안내 문구 (단계 시작)
        add("화성 단계: 모든 구역에 달이 하나씩 더 있습니다. 달은 발사하는 순간부터 돌기 시작합니다.",
            "Mars stage: every zone has one extra moon. Moons start orbiting the moment you launch.",
            "火星ステージ：すべての区域に月が1つ多くあります。月は発射した瞬間から回り始めます。",
            "火星阶段：每个区域多一颗月球。月球从发射那一刻开始转动。");
        add("금성 단계: 두꺼운 대기처럼 소포를 밀어내는 별이 하나씩 더 있습니다.",
            "Venus stage: like a thick atmosphere, each zone has one extra star that pushes your parcel away.",
            "金星ステージ：厚い大気のように、小包を押し返す星が1つ多くあります。",
            "金星阶段：像厚厚的大气层一样，每个区域多一颗推开包裹的星星。");
        add("수성 단계: 태양 가까운 곳입니다. 모든 구역에 블랙홀이 하나씩 더 있습니다.",
            "Mercury stage: close to the Sun. Every zone has one extra black hole.",
            "水星ステージ：太陽の近くです。すべての区域にブラックホールが1つ多くあります。",
            "水星阶段：靠近太阳。每个区域多一个黑洞。");
        add("목성 단계: 행성과 달이 하나씩 더 많습니다. 점선이 짧아졌으니 궤도를 상상해 보세요.",
            "Jupiter stage: one more planet and one more moon. The dotted line is shorter, so imagine the rest of the orbit.",
            "木星ステージ：惑星と月が1つずつ多くなります。点線が短くなったので、軌道を想像してみましょう。",
            "木星阶段：行星和月球各多一个。虚线变短了，试着想象剩下的轨道。");
        add("토성 단계: 달과 웜홀이 더해집니다.",
            "Saturn stage: moons and a wormhole are added.",
            "土星ステージ：月とワームホールが加わります。",
            "土星阶段：增加了月球和虫洞。");
        add("천왕성 단계: 행성, 밀어내는 별, 웜홀이 하나씩 더 있습니다.",
            "Uranus stage: one extra planet, pushing star and wormhole.",
            "天王星ステージ：惑星、押し返す星、ワームホールが1つずつ多くあります。",
            "天王星阶段：行星、推开的星星和虫洞各多一个。");
        add("해왕성 단계: 행성, 블랙홀, 밀어내는 별이 하나씩 더 있습니다.",
            "Neptune stage: one extra planet, black hole and pushing star.",
            "海王星ステージ：惑星、ブラックホール、押し返す星が1つずつ多くあります。",
            "海王星阶段：行星、黑洞和推开的星星各多一个。");
        add("태양 단계: 마지막입니다. 모든 천체가 하나씩 더 있고, 궤도는 더 크게 휘어야 합니다.",
            "Sun stage: the last one. Every kind of body gets one more, and your orbit must bend even further.",
            "太陽ステージ：最後です。すべての天体が1つずつ多く、軌道はさらに大きく曲げる必要があります。",
            "太阳阶段：最后一关。每种天体各多一个，轨道需要弯得更厉害。");

        // 버그 제보 창
        add("이상한 점을 알려 주세요. GitHub 제보 양식이 미리 채워진 채로 열립니다.",
            "Tell us what went wrong. The GitHub report form opens already filled in.",
            "おかしな点を教えてください。GitHubの報告フォームが入力済みの状態で開きます。",
            "请告诉我们哪里出了问题。GitHub报告表单会自动填好并打开。");
        add("무엇이 잘못됐나요?", "What went wrong?", "何がおかしかったですか？", "出了什么问题？");
        add("어떻게 하면 다시 생기나요?", "How can it be reproduced?", "どうすると再現しますか？", "怎样才能重现？");
        add("자동으로 붙는 정보", "Attached automatically", "自動で添付される情報", "自动附加的信息");
        add("GitHub에 제보하기", "Report on GitHub", "GitHubで報告", "在GitHub上报告");
        add("내용 복사", "Copy", "内容をコピー", "复制内容");
        add("복사했습니다", "Copied", "コピーしました", "已复制");
        add("닫기", "Close", "閉じる", "关闭");
        add("무엇이 잘못됐는지 한 줄이라도 적어 주세요.",
            "Please write at least one line about what went wrong.",
            "何がおかしかったか、1行でも書いてください。",
            "请至少写一行说明出了什么问题。");
        add("브라우저를 열지 못해서 제보 주소를 복사했습니다.\n브라우저 주소창에 붙여 넣어 주세요.",
            "Couldn't open the browser, so the report link was copied.\nPaste it into your browser's address bar.",
            "ブラウザを開けなかったので、報告用アドレスをコピーしました。\nブラウザのアドレス欄に貼り付けてください。",
            "无法打开浏览器，已复制报告地址。\n请粘贴到浏览器地址栏。");
        add("버전", "Version", "バージョン", "版本");
        add("화면", "Screen", "画面", "画面");
        add("구역 불러오는 중", "Loading zone", "区域を読み込み中", "正在加载区域");
        add("구역 목록 (%s 단계)", "Zones (%s stage)", "区域一覧（%sステージ）", "区域列表（%s阶段）");
        add("%s 단계 %02d · %s (씨앗 %d)", "%s stage %02d · %s (seed %d)", "%sステージ %02d · %s（シード %d）", "%s阶段 %02d · %s（种子 %d）");
        add("운영체제", "OS", "OS", "操作系统");
        add("자바", "Java", "Java", "Java");
        add("창 크기", "Window size", "ウィンドウサイズ", "窗口大小");
        add("화면 배율", "Display scale", "画面の拡大率", "屏幕缩放");

        // 후원 창
        add("별똥 택배가 재미있었다면 후원으로 응원해 주세요.",
            "If you enjoyed StarDrop, please support it with a donation.",
            "流れ星宅配便を楽しんでいただけたら、寄付で応援してください。",
            "如果你喜欢流星快递，欢迎赞助支持。");
        add("후원금은 새 구역과 기능을 만드는 데 쓰입니다.",
            "Donations go toward new zones and features.",
            "寄付金は新しい区域と機能を作るために使われます。",
            "赞助款将用于制作新区域和新功能。");
        add("금액 (최소 %,d원)", "Amount (min. ₩%,d)", "金額（最低%,dウォン）", "金额（最低%,d韩元）");
        add("%,d원", "₩%,d", "%,dウォン", "%,d韩元");
        add("직접 입력", "Custom", "直接入力", "自定义");
        add("금액을 숫자로 적어 주세요.", "Please enter the amount as a number.", "金額を数字で入力してください。", "请用数字输入金额。");
        add("최소 %,d원부터 후원할 수 있어요.", "The minimum donation is ₩%,d.", "%,dウォンから寄付できます。", "最低赞助金额为%,d韩元。");
        add("한 번에 %,d원까지 후원할 수 있어요.", "You can donate up to ₩%,d at a time.", "1回に%,dウォンまで寄付できます。", "单次最多可赞助%,d韩元。");
        add("금액을 적어 주세요.", "Please enter an amount.", "金額を入力してください。", "请输入金额。");
        add("송금 화면에서 %,d원을 입력해 주세요.", "Enter ₩%,d on the transfer screen.", "送金画面で%,dウォンを入力してください。", "请在转账页面输入%,d韩元。");
        add("금액을 먼저 골라 주세요", "Choose an amount first", "先に金額を選んでください", "请先选择金额");
        add("후원 링크 준비 중", "Donation link coming soon", "寄付リンク準備中", "赞助链接准备中");
        add("휴대폰 카메라나 카카오톡으로 QR을 찍으면 카카오페이 송금 화면이 열려요.",
            "Scan the QR code with your phone camera or KakaoTalk to open the KakaoPay transfer screen.",
            "スマホのカメラかカカオトークでQRを読み取ると、カカオペイの送金画面が開きます。",
            "用手机相机或KakaoTalk扫描二维码，即可打开KakaoPay转账页面。");
        add("링크 복사", "Copy Link", "リンクをコピー", "复制链接");
        add("링크를 복사했어요. 휴대폰으로 보내서 열어 주세요.",
            "Link copied. Send it to your phone and open it there.",
            "リンクをコピーしました。スマホに送って開いてください。",
            "链接已复制。请发送到手机上打开。");
        add("복사하지 못했어요: ", "Couldn't copy: ", "コピーできませんでした: ", "无法复制：");

        // 언어 고르는 창
        add("게임에서 쓸 언어를 고르세요.", "Choose the language for the game.", "ゲームで使う言語を選んでください。", "请选择游戏语言。");
        // 스킨
        add("스킨", "Skins", "スキン", "皮肤");
        add("기본 소포", "Classic Parcel", "いつもの小包", "经典包裹");
        add("민트 혜성", "Mint Comet", "ミント彗星", "薄荷彗星");
        add("벚꽃 유성", "Cherry Blossom Meteor", "桜の流れ星", "樱花流星");
        add("얼음 혜성", "Ice Comet", "氷の彗星", "冰晶彗星");
        add("태양 불꽃", "Solar Flare", "太陽フレア", "太阳耀斑");
        add("은하수 소포", "Milky Way Parcel", "天の川の小包", "银河包裹");
        add("황금 택배", "Golden Delivery", "黄金の宅配便", "黄金快递");
        add("소포와 꼬리 모양을 바꿔요.", "Change how your parcel and its tail look.", "小包と尾の見た目を変えられます。", "更换包裹和尾迹的外观。");
        add("모은 별 ★ %d / %d", "Stars collected ★ %d / %d", "集めた星 ★ %d / %d", "已收集星星 ★ %d / %d");
        add("사용 중", "In use", "使用中", "使用中");
        add("고르기", "Select", "選ぶ", "选择");
        add("★ %d개 필요", "Needs ★ %d", "★ %d個で解放", "需要 ★ %d");
        add("%,d원 · 코드로 열기", "₩%,d · unlock with a code", "%,dウォン・コードで解放", "%,d韩元 · 用兑换码解锁");
        add("업적 보상", "Achievement reward", "実績報酬", "成就奖励");
        add("이제 「%s」 스킨을 써요.", "Now using the \"%s\" skin.", "「%s」スキンに変えました。", "已换成「%s」皮肤。");
        add("별을 %d개 더 모으면 열려요.", "Collect %d more stars to unlock it.", "あと星を%d個集めると解放されます。", "再收集%d颗星星即可解锁。");
        add("업적을 달성하면 열려요.", "Unlocks when you earn an achievement.", "実績を達成すると解放されます。", "达成成就后解锁。");
        add("「%s」 열기 · %,d원", "Unlock \"%s\" · ₩%,d", "「%s」を解放・%,dウォン", "解锁「%s」· %,d韩元");
        add("1. QR을 찍어 카카오페이로 %,d원을 보내 주세요.", "1. Scan the QR code and send ₩%,d with KakaoPay.",
            "1. QRを読み取り、カカオペイで%,dウォンを送金してください。", "1. 扫描二维码，用KakaoPay转账%,d韩元。");
        add("2. 송금 메시지에 아래 기기 번호를 적어 주세요.", "2. Write the device number below in the transfer message.",
            "2. 送金メッセージに下の端末番号を書いてください。", "2. 请在转账留言中填写下面的设备编号。");
        add("복사", "Copy", "コピー", "复制");
        add("코드 넣기", "Redeem", "コードを入力", "兑换");
        add("코드는 이 컴퓨터에서만 열려요. 송금은 개발자가 직접 확인해서 조금 걸릴 수 있어요.",
            "Codes only work on this computer. Transfers are checked by hand, so it may take a while.",
            "コードはこのパソコンでのみ使えます。送金は開発者が手で確認するため、少し時間がかかることがあります。",
            "兑换码只能在这台电脑上使用。转账由开发者手动确认，可能需要一些时间。");
        add("기기 번호를 복사했어요.", "Device number copied.", "端末番号をコピーしました。", "设备编号已复制。");
        add("코드를 붙여 넣어 주세요.", "Please paste your code.", "コードを貼り付けてください。", "请粘贴兑换码。");
        add("맞지 않는 코드예요. 이 컴퓨터의 기기 번호로 받은 코드인지 확인해 주세요.",
            "That code doesn't match. Make sure it was issued for this computer's device number.",
            "コードが一致しません。このパソコンの端末番号で発行されたコードか確認してください。",
            "兑换码不匹配。请确认它是用这台电脑的设备编号申请的。");
        add("「%s」 스킨이 열렸어요! 고마워요.", "The \"%s\" skin is unlocked! Thank you.", "「%s」スキンが解放されました！ありがとうございます。", "「%s」皮肤已解锁！谢谢支持。");
        add("3. 입금자명을 적고 코드 요청을 보내 주세요. 송금이 확인되면 스킨이 자동으로 열려요.",
            "3. Enter the sender name and send a code request. The skin unlocks automatically once the transfer is confirmed.",
            "3. 送金者名を入力してコードを申請してください。送金が確認されるとスキンが自動で解放されます。",
            "3. 填写转账人姓名并发送兑换码申请。确认转账后皮肤会自动解锁。");
        add("입금자명 (선택)", "Sender name (optional)", "送金者名（任意）", "转账人姓名（可选）");
        add("코드 요청 보내기", "Send Code Request", "コードを申請", "发送兑换码申请");
        add("지금 확인", "Check Now", "今すぐ確認", "立即检查");
        add("코드를 따로 받았다면", "Got a code another way?", "コードを別に受け取った場合", "如果另外收到了兑换码");
        add("송금 확인 중", "Checking transfer", "送金確認中", "正在确认转账");
        add("코드 요청은 준비 중이에요.", "Code requests aren't available yet.", "コード申請は準備中です。", "兑换码申请功能准备中。");
        add("요청을 받았어요. 송금이 확인되면 자동으로 열려요.", "Request received. The skin unlocks automatically once the transfer is confirmed.",
            "申請を受け付けました。送金が確認されると自動で解放されます。", "已收到申请。确认转账后会自动解锁。");
        add("송금을 찾지 못했어요. 송금한 뒤 다시 요청해 주세요.", "We couldn't find your transfer. Please send it and request again.",
            "送金が見つかりませんでした。送金してからもう一度申請してください。", "未找到您的转账。请转账后重新申请。");
        add("확인하는 중…", "Checking…", "確認中…", "正在检查…");
        add("보내는 중…", "Sending…", "送信中…", "正在发送…");
        add("서버에 연결하지 못했어요. 인터넷을 확인해 주세요.", "Couldn't reach the server. Please check your internet connection.",
            "サーバーに接続できませんでした。インターネット接続を確認してください。", "无法连接服务器。请检查网络连接。");
        add("새로 발급된 코드가 없어요.", "No new codes yet.", "新しく発行されたコードはありません。", "暂无新发放的兑换码。");
        add("아직 송금을 확인하는 중이에요.", "Still checking your transfer.", "まだ送金を確認しています。", "仍在确认您的转账。");
        return table;
    }
}
