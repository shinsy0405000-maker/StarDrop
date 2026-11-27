import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.StringSelection;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 스킨 고르는 창.
 *
 * 스킨 카드마다 미리보기(소포와 꼬리)와 얻는 방법이 나온다.
 *   - 열린 스킨을 누르면 바로 바뀐다.
 *   - 별로 여는 스킨은 별이 몇 개 더 필요한지 알려 준다.
 *   - 유료 스킨을 누르면 아래에 여는 방법이 나온다 (계정 필요 없음):
 *       1. 카카오페이 QR로 송금 (송금 메시지에 기기 번호)
 *       2. "코드 요청 보내기" → 서버(SkinServer, Supabase)에 요청이 저장된다
 *       3. 개발자가 송금을 확인하고 코드를 발급하면, 이 창을 열 때(또는 "지금 확인") 자동으로 열린다
 *   - 서버가 안 될 때를 위해 코드를 직접 넣는 칸도 있다.
 */
final class SkinPicker {
    private SkinPicker() { }

    static void open(Component parent, int totalStars, Consumer<Skin> onPick) {
        Window owner = SwingUtilities.getWindowAncestor(parent);
        JDialog dialog = new JDialog(owner, Lang.t("스킨"), JDialog.ModalityType.APPLICATION_MODAL);

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBackground(Ui.PANEL);
        body.setBorder(BorderFactory.createEmptyBorder(22, 24, 22, 24));
        body.add(label(Lang.t("스킨"), Ui.display(26, 0), Ui.INK));
        body.add(Box.createVerticalStrut(6));
        body.add(label(Lang.t("소포와 꼬리 모양을 바꿔요.") + "  " + Lang.f("모은 별 ★ %d / %d", totalStars, Campaign.COUNT * 3),
                Ui.font(13, Font.PLAIN, false, 0), Ui.DIM));
        body.add(Box.createVerticalStrut(14));

        // 스킨 카드 (두 줄씩)
        JPanel grid = new JPanel(new GridLayout(0, 2, 8, 8));
        grid.setBackground(Ui.PANEL);
        grid.setAlignmentX(Component.LEFT_ALIGNMENT);
        List<JButton> cards = new ArrayList<>();
        for (Skin skin : Skin.values()) {
            JButton card = new JButton(new ImageIcon(preview(skin, 132, 58)));
            card.setHorizontalAlignment(SwingConstants.LEFT);
            card.setIconTextGap(12);
            card.setFocusPainted(false);
            card.setContentAreaFilled(false);
            card.setOpaque(true);
            card.setBackground(Ui.VOID);
            card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            card.setFont(Ui.font(14, Ui.MEDIUM, false, 0));
            cards.add(card);
            grid.add(card);
        }
        body.add(grid);
        body.add(Box.createVerticalStrut(10));

        JLabel status = label(" ", Ui.font(13, Font.PLAIN, false, 0), Ui.MINT);
        body.add(status);
        body.add(Box.createVerticalStrut(6));

        // ---------------- 유료 스킨 여는 방법 (유료 스킨을 누르면 보인다)
        JPanel buy = new JPanel();
        buy.setLayout(new BoxLayout(buy, BoxLayout.Y_AXIS));
        buy.setBackground(Ui.PANEL);
        buy.setAlignmentX(Component.LEFT_ALIGNMENT);
        buy.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Ui.LINE), BorderFactory.createEmptyBorder(14, 16, 14, 16)));
        buy.setVisible(false);
        JLabel buyTitle = label(" ", Ui.font(16, Font.BOLD, false, 0), Ui.AMBER);
        buy.add(buyTitle);
        buy.add(Box.createVerticalStrut(10));

        String device = SkinStore.device();
        JPanel steps = row();
        JLabel qr = new JLabel();
        if (Donate.ready()) qr.setIcon(new ImageIcon(QrCode.encode(Donate.payUrl()).toImage(4, Color.BLACK, Color.WHITE)));
        qr.setAlignmentY(Component.TOP_ALIGNMENT);
        steps.add(qr);
        steps.add(Box.createHorizontalStrut(14));
        JPanel right = new JPanel();
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        right.setBackground(Ui.PANEL);
        right.setAlignmentY(Component.TOP_ALIGNMENT);
        JLabel step1 = label(" ", Ui.font(13, Font.PLAIN, false, 0), Ui.SOFT_INK);
        right.add(step1);
        right.add(Box.createVerticalStrut(6));
        right.add(label("<html>" + Lang.t("2. 송금 메시지에 아래 기기 번호를 적어 주세요.") + "</html>", Ui.font(13, Font.PLAIN, false, 0), Ui.SOFT_INK));
        right.add(Box.createVerticalStrut(4));
        JPanel deviceRow = row();
        deviceRow.add(label(device, Ui.font(17, Font.BOLD, true, 0.08f), Ui.INK));
        deviceRow.add(Box.createHorizontalStrut(10));
        JButton copyDevice = button(Lang.t("복사"), false);
        deviceRow.add(copyDevice);
        right.add(deviceRow);
        right.add(Box.createVerticalStrut(8));
        right.add(label("<html>" + Lang.t("3. 입금자명을 적고 코드 요청을 보내 주세요. 송금이 확인되면 스킨이 자동으로 열려요.") + "</html>",
                Ui.font(13, Font.PLAIN, false, 0), Ui.SOFT_INK));
        right.add(Box.createVerticalStrut(4));
        JPanel requestRow = row();
        JTextField payerField = field(false);
        payerField.setToolTipText(Lang.t("입금자명 (선택)"));
        requestRow.add(payerField);
        requestRow.add(Box.createHorizontalStrut(8));
        JButton request = button(Lang.t("코드 요청 보내기"), true);
        requestRow.add(request);
        right.add(label(Lang.t("입금자명 (선택)"), Ui.font(12, Font.PLAIN, false, 0), Ui.DIM));
        right.add(Box.createVerticalStrut(2));
        right.add(requestRow);
        right.add(Box.createVerticalStrut(6));
        JLabel requestState = label(" ", Ui.font(13, Font.PLAIN, false, 0), Ui.MINT);
        right.add(requestState);
        right.add(Box.createVerticalStrut(4));
        JButton check = button(Lang.t("지금 확인"), false);
        right.add(check);
        steps.add(right);
        buy.add(steps);
        buy.add(Box.createVerticalStrut(12));

        // 서버가 안 될 때: 코드를 직접 넣는 칸
        buy.add(label(Lang.t("코드를 따로 받았다면"), Ui.font(12, Font.BOLD, false, 0), Ui.DIM));
        buy.add(Box.createVerticalStrut(4));
        JPanel codeRow = row();
        JTextField codeField = field(true);
        codeRow.add(codeField);
        codeRow.add(Box.createHorizontalStrut(8));
        JButton redeem = button(Lang.t("코드 넣기"), false);
        codeRow.add(redeem);
        buy.add(codeRow);
        buy.add(Box.createVerticalStrut(6));
        buy.add(label(Lang.t("코드는 이 컴퓨터에서만 열려요. 송금은 개발자가 직접 확인해서 조금 걸릴 수 있어요."), Ui.font(12, Font.PLAIN, false, 0), Ui.DIM));
        body.add(buy);
        body.add(Box.createVerticalStrut(14));

        JPanel bottom = row();
        bottom.add(Box.createHorizontalGlue());
        JButton close = button(Lang.t("닫기"), false);
        bottom.add(close);
        body.add(bottom);

        // ---------------- 동작
        int stars = totalStars;
        Skin[] shown = {null};                                   // 아래에 여는 방법이 나와 있는 유료 스킨
        Skin[] current = {SkinStore.selected(totalStars)};
        Set<Skin> pending = EnumSet.noneOf(Skin.class), rejected = EnumSet.noneOf(Skin.class);   // 서버에서 받은 요청 상태
        Runnable refresh = () -> {
            for (int i = 0; i < cards.size(); i++) {
                Skin s = Skin.values()[i];
                boolean open = SkinStore.unlocked(s, stars), now = s == current[0];
                String state = now ? Lang.t("사용 중") : open ? Lang.t("고르기")
                        : switch (s.unlock) {
                            case STARS -> Lang.f("★ %d개 필요", s.need);
                            case PAID -> pending.contains(s) ? Lang.t("송금 확인 중") : Lang.f("%,d원 · 코드로 열기", s.need);
                            default -> Lang.t("업적 보상");
                        };
                String color = now ? "#ffc65c" : open ? "#6ff2c4" : "#8f94bd";
                cards.get(i).setText("<html>" + Lang.t(s.name) + "<br><span style='font-size:90%;color:" + color + "'>" + state + "</span></html>");
                cards.get(i).setForeground(open ? Ui.INK : Ui.DIM);
                Color edge = now ? Ui.AMBER : s == shown[0] ? Ui.AMBER_LIGHT : Ui.LINE;
                cards.get(i).setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(edge, now ? 2 : 1),
                        BorderFactory.createEmptyBorder(now ? 7 : 8, now ? 9 : 10, now ? 7 : 8, now ? 9 : 10)));
            }
        };
        Runnable showBuy = () -> {
            Skin s = shown[0];
            buy.setVisible(s != null);
            if (s != null) {
                buyTitle.setText(Lang.f("「%s」 열기 · %,d원", Lang.t(s.name), s.need));
                step1.setText("<html>" + Lang.f("1. QR을 찍어 카카오페이로 %,d원을 보내 주세요.", s.need) + "</html>");
                if (!SkinServer.ready()) {
                    requestState.setForeground(Ui.DIM);
                    requestState.setText(Lang.t("코드 요청은 준비 중이에요."));
                } else if (pending.contains(s)) {
                    requestState.setForeground(Ui.MINT);
                    requestState.setText(Lang.t("요청을 받았어요. 송금이 확인되면 자동으로 열려요."));
                } else if (rejected.contains(s)) {
                    requestState.setForeground(Ui.DANGER);
                    requestState.setText(Lang.t("송금을 찾지 못했어요. 송금한 뒤 다시 요청해 주세요."));
                } else {
                    requestState.setText(" ");
                }
                request.setEnabled(SkinServer.ready() && !pending.contains(s));
                check.setEnabled(SkinServer.ready());
            }
            dialog.pack();
            dialog.setSize(620, dialog.getHeight());
        };
        // 열렸을 때 고르고 알린다 (서버 확인과 코드 넣기가 같이 쓴다)
        Consumer<Skin> opened = got -> {
            current[0] = got;
            SkinStore.select(got);
            onPick.accept(got);
            if (shown[0] == got) shown[0] = null;
            status.setForeground(Ui.MINT);
            status.setText(Lang.f("「%s」 스킨이 열렸어요! 고마워요.", Lang.t(got.name)));
        };
        // 서버에서 요청 상태를 받아 온다 (인터넷을 쓰므로 따로 도는 스레드에서)
        Consumer<Boolean> sync = loud -> {
            if (!SkinServer.ready()) return;
            check.setEnabled(false);
            if (loud) {
                status.setForeground(Ui.DIM);
                status.setText(Lang.t("확인하는 중…"));
            }
            Thread t = new Thread(() -> {
                SkinStore.Sync result = null;
                try {
                    result = SkinStore.sync();
                } catch (Exception ex) {
                    // 아래에서 알린다
                }
                SkinStore.Sync r = result;
                SwingUtilities.invokeLater(() -> {
                    if (r == null) {
                        if (loud) {
                            status.setForeground(Ui.DANGER);
                            status.setText(Lang.t("서버에 연결하지 못했어요. 인터넷을 확인해 주세요."));
                        }
                    } else {
                        pending.clear();
                        pending.addAll(r.pending);
                        rejected.clear();
                        rejected.addAll(r.rejected);
                        if (!r.unlocked.isEmpty()) opened.accept(r.unlocked.get(0));
                        else if (loud) {
                            status.setForeground(Ui.DIM);
                            status.setText(r.pending.isEmpty() ? Lang.t("새로 발급된 코드가 없어요.") : Lang.t("아직 송금을 확인하는 중이에요."));
                        }
                    }
                    refresh.run();
                    showBuy.run();
                });
            }, "skin-sync");
            t.setDaemon(true);
            t.start();
        };
        for (int i = 0; i < cards.size(); i++) {
            Skin s = Skin.values()[i];
            cards.get(i).addActionListener(e -> {
                if (SkinStore.unlocked(s, stars)) {
                    current[0] = s;
                    SkinStore.select(s);
                    onPick.accept(s);
                    shown[0] = null;
                    status.setForeground(Ui.MINT);
                    status.setText(Lang.f("이제 「%s」 스킨을 써요.", Lang.t(s.name)));
                } else if (s.unlock == Skin.Unlock.STARS) {
                    shown[0] = null;
                    status.setForeground(Ui.DIM);
                    status.setText(Lang.f("별을 %d개 더 모으면 열려요.", s.need - stars));
                } else if (s.unlock == Skin.Unlock.PAID) {
                    shown[0] = s;
                    status.setText(" ");
                } else {
                    shown[0] = null;
                    status.setForeground(Ui.DIM);
                    status.setText(Lang.t("업적을 달성하면 열려요."));
                }
                refresh.run();
                showBuy.run();
            });
        }
        copyDevice.addActionListener(e -> {
            try {
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(device), null);
                status.setForeground(Ui.MINT);
                status.setText(Lang.t("기기 번호를 복사했어요."));
            } catch (Exception ex) {
                status.setText(device);
            }
        });
        request.addActionListener(e -> {
            Skin s = shown[0];
            if (s == null || !SkinServer.ready()) return;
            request.setEnabled(false);
            requestState.setForeground(Ui.DIM);
            requestState.setText(Lang.t("보내는 중…"));
            String payer = payerField.getText();
            Thread t = new Thread(() -> {
                String answer;
                try {
                    answer = SkinServer.request(device, s, payer);
                } catch (Exception ex) {
                    answer = null;
                }
                String a = answer;
                SwingUtilities.invokeLater(() -> {
                    if (a == null) {
                        requestState.setForeground(Ui.DANGER);
                        requestState.setText(Lang.t("서버에 연결하지 못했어요. 인터넷을 확인해 주세요."));
                        request.setEnabled(true);
                    } else if (a.equals("issued")) {
                        sync.accept(true);                   // 이미 발급됨 → 받아서 연다
                    } else {
                        pending.add(s);
                        rejected.remove(s);
                        refresh.run();
                        showBuy.run();
                    }
                });
            }, "skin-request");
            t.setDaemon(true);
            t.start();
        });
        check.addActionListener(e -> sync.accept(true));
        Runnable tryCode = () -> {
            String code = codeField.getText().strip();
            if (code.isEmpty()) {
                status.setForeground(Ui.DANGER);
                status.setText(Lang.t("코드를 붙여 넣어 주세요."));
                return;
            }
            Skin got = SkinStore.redeem(code);
            if (got == null) {
                status.setForeground(Ui.DANGER);
                status.setText(Lang.t("맞지 않는 코드예요. 이 컴퓨터의 기기 번호로 받은 코드인지 확인해 주세요."));
                return;
            }
            codeField.setText("");
            opened.accept(got);
            refresh.run();
            showBuy.run();
        };
        redeem.addActionListener(e -> tryCode.run());
        codeField.addActionListener(e -> tryCode.run());
        close.addActionListener(e -> dialog.dispose());

        refresh.run();
        sync.accept(false);      // 창을 열면 조용히 확인: 발급된 코드가 있으면 바로 열린다
        dialog.getContentPane().setBackground(Ui.PANEL);
        dialog.add(body, BorderLayout.CENTER);
        dialog.pack();
        dialog.setSize(620, dialog.getHeight());
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
    }

    /** 카드에 넣을 미리보기: 어두운 우주 위에 휘어 날아가는 소포와 꼬리 */
    static BufferedImage preview(Skin skin, int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setColor(Ui.VOID);
        g.fillRect(0, 0, w, h);
        List<double[]> trail = new ArrayList<>();
        int n = 80;
        // 곡선(2차 베지어): 왼쪽 아래 → 위로 휘었다가 → 오른쪽
        double x0 = 8, y0 = h * 0.82, cx = w * 0.45, cy = h * 0.02, x1 = w - 22, y1 = h * 0.45;
        for (int i = 0; i < n; i++) {
            double t = i / (double) (n - 1), u = 1 - t;
            trail.add(new double[]{u * u * x0 + 2 * u * t * cx + t * t * x1, u * u * y0 + 2 * u * t * cy + t * t * y1});
        }
        double[] a = trail.get(n - 2), b = trail.get(n - 1);
        skin.drawTail(g, trail, 0.7);
        skin.drawHead(g, b[0], b[1], 20, 6, 2.8, 191, Math.atan2(b[1] - a[1], b[0] - a[0]), 0.7);
        g.dispose();
        return img;
    }

    // ------------------------------------------------------------ 게임과 어울리는 어두운 모양의 부품들 (후원 창과 같은 모양)
    private static JPanel row() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.X_AXIS));
        p.setBackground(Ui.PANEL);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        return p;
    }

    private static JLabel label(String text, Font font, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(font);
        l.setForeground(color);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private static JTextField field(boolean mono) {
        JTextField f = new JTextField();
        f.setFont(Ui.font(13, Font.PLAIN, mono, 0));
        f.setBackground(Ui.VOID);
        f.setForeground(Ui.INK);
        f.setCaretColor(Ui.AMBER);
        f.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Ui.LINE), BorderFactory.createEmptyBorder(7, 10, 7, 10)));
        f.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        return f;
    }

    private static JButton button(String text, boolean primary) {
        JButton b = new JButton(text);
        b.setFont(Ui.font(14, primary ? Font.BOLD : Ui.MEDIUM, false, 0));
        b.setFocusPainted(false);
        b.setContentAreaFilled(false);
        b.setOpaque(true);
        b.setBackground(primary ? Ui.AMBER : Ui.PANEL);
        b.setForeground(primary ? Ui.DARK_INK : Ui.INK);
        b.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(primary ? Ui.AMBER : Ui.LINE),
                BorderFactory.createEmptyBorder(7, 14, 7, 14)));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
        return b;
    }
}
