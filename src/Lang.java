import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 언어 설정: 한국어, English, 日本語, 中文 네 가지.
 *
 * 게임 안의 글은 한국어로 적어 두고, 화면에 그릴 때 Lang.t("배달 시작") 처럼 감싸면
 * 지금 고른 언어의 글로 바꿔 준다. 표에 없는 글은 한국어 그대로 나온다.
 * 숫자가 들어가는 글은 Lang.f("%d회", 3) 처럼 쓴다 (String.format 과 같다).
 *
 * 고른 언어는 진행 기록과 같은 곳(Preferences)에 저장되고, 처음 켤 때는 컴퓨터 언어를 따른다.
 */
final class Lang {
    private Lang() { }

    enum Code {
        KO("한국어", "ko"), EN("English", "en"), JA("日本語", "ja"), ZH("中文", "zh");

        final String nativeName;   // 그 언어로 쓴 언어 이름 (고르는 창에 보인다)
        final String tag;          // 저장할 때 쓰는 짧은 이름

        Code(String nativeName, String tag) {
            this.nativeName = nativeName;
            this.tag = tag;
        }
    }

    private static Code current = load();

    static Code current() {
        return current;
    }

    /** 일본어나 중국어처럼 한자·가나 글꼴이 필요한 언어인가 */
    static boolean cjk() {
        return current == Code.JA || current == Code.ZH;
    }

    static void set(Code code) {
        current = code;
        try {
            java.util.prefs.Preferences.userRoot().node("stardrop").put("lang", code.tag);
        } catch (Exception e) {
            // 저장하지 못해도 이번 실행에서는 바뀐다
        }
    }

    private static Code load() {
        String saved = null;
        try {
            saved = java.util.prefs.Preferences.userRoot().node("stardrop").get("lang", null);
        } catch (Exception e) {
            // 읽지 못하면 컴퓨터 언어를 따른다
        }
        String tag = saved != null ? saved : Locale.getDefault().getLanguage();
        for (Code c : Code.values()) if (c.tag.equals(tag)) return c;
        return saved == null && !tag.isEmpty() ? Code.EN : Code.KO;     // 네 언어가 아닌 컴퓨터는 영어로
    }

    /** 한국어 글 → 지금 언어의 글 */
    static String t(String ko) {
        if (ko == null || current == Code.KO) return ko;
        String[] row = TABLE.get(ko);
        return row == null ? ko : row[current.ordinal() - 1];
    }

    /** 숫자 등이 들어가는 글 (String.format 과 같다) */
    static String f(String ko, Object... args) {
        return String.format(t(ko), args);
    }

    /** 표에 이 글이 있는가 (확인용) */
    static boolean has(String ko) {
        return TABLE.containsKey(ko);
    }

    // ------------------------------------------------------------ 번역 표: 한국어 → {English, 日本語, 中文}
    private static final Map<String, String[]> TABLE = new HashMap<>();

    private static void add(String ko, String en, String ja, String zh) {
        TABLE.put(ko, new String[]{en, ja, zh});
    }

    static {
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
    }
}
