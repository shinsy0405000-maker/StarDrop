import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.StringSelection;

/**
 * 후원 창 (카카오페이 송금코드).
 *
 * 금액(5,000원 / 10,000원 / 30,000원 / 직접 입력)을 고르면 카카오페이 송금 QR이 나온다.
 * 휴대폰 카메라나 카카오톡으로 QR을 찍으면 카카오페이 송금 화면이 열리고, 거기서 고른 금액을 보내면 된다.
 * 5,000원보다 적은 금액을 고르면 QR이 나오지 않는다.
 *
 * 카카오페이 송금은 휴대폰 카카오톡 안에서만 되기 때문에, PC 게임에서는 QR을 보여 주는 게 가장 편하다.
 * PC 브라우저로 이 주소를 열면 카카오페이가 404(없는 페이지)를 보여 줘서 "링크 열기" 버튼은 두지 않는다.
 * 대신 "링크 복사" 버튼으로 휴대폰에 보내서 열 수 있게 한다.
 * 송금코드 링크가 아직 없으면 KAKAOPAY_LINK 를 비워 두고, 생기면 이 한 줄만 바꾸면 된다.
 */
final class Donate {
    private Donate() { }

    /** 카카오페이 송금코드 링크 (https://qr.kakaopay.com/... 전체, 또는 / 뒤의 코드만). 비어 있으면 "준비 중" */
    static final String KAKAOPAY_LINK = "https://qr.kakaopay.com/FVVslcpd2";

    static final int MIN_AMOUNT = 5_000;           // 최소 후원 금액(원)
    static final int MAX_AMOUNT = 10_000_000;      // 잘못 입력한 큰 숫자를 막기 위한 한도
    static final int[] PRESETS = {5_000, 10_000, 30_000};

    static boolean ready() {
        return !KAKAOPAY_LINK.isBlank();
    }

    /** 송금코드 주소. 코드만 적었으면 앞에 주소를 붙여 준다 */
    static String payUrl() {
        String s = KAKAOPAY_LINK.strip();
        return s.startsWith("http") ? s : "https://qr.kakaopay.com/" + s;
    }

    /** "12,000" 이나 "12000원" 같은 글에서 금액을 읽는다. 숫자가 없으면 -1 */
    static int parseAmount(String text) {
        String digits = text.replaceAll("[^0-9]", "");
        if (digits.isEmpty() || digits.length() > 9) return -1;
        return Integer.parseInt(digits);
    }

    /** 이 금액으로 후원할 수 있으면 null, 안 되면 이유 */
    static String check(int amount) {
        if (amount < 0) return Lang.t("금액을 숫자로 적어 주세요.");
        if (amount < MIN_AMOUNT) return Lang.f("최소 %,d원부터 후원할 수 있어요.", MIN_AMOUNT);
        if (amount > MAX_AMOUNT) return Lang.f("한 번에 %,d원까지 후원할 수 있어요.", MAX_AMOUNT);
        return null;
    }

    static void open(Component parent) {
        Window owner = SwingUtilities.getWindowAncestor(parent);
        JDialog dialog = new JDialog(owner, Lang.t("후원하기"), JDialog.ModalityType.APPLICATION_MODAL);

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBackground(Ui.PANEL);
        body.setBorder(BorderFactory.createEmptyBorder(22, 24, 18, 24));
        body.add(label(Lang.t("후원하기"), Ui.display(26, 0), Ui.INK));
        body.add(Box.createVerticalStrut(8));
        body.add(label("<html>" + Lang.t("별똥 택배가 재미있었다면 후원으로 응원해 주세요.") + "<br>" + Lang.t("후원금은 새 구역과 기능을 만드는 데 쓰입니다.") + "</html>",
                Ui.font(14, Font.PLAIN, false, 0), Ui.SOFT_INK));
        body.add(Box.createVerticalStrut(18));
        body.add(label(Lang.f("금액 (최소 %,d원)", MIN_AMOUNT), Ui.font(13, Font.BOLD, false, 0), Ui.SOFT_INK));
        body.add(Box.createVerticalStrut(8));

        // 금액 고르기: 정해진 금액 3개 + 직접 입력
        JPanel presets = row();
        ButtonGroup group = new ButtonGroup();
        JToggleButton[] choices = new JToggleButton[PRESETS.length + 1];
        for (int i = 0; i <= PRESETS.length; i++) {
            choices[i] = choice(i < PRESETS.length ? Lang.f("%,d원", PRESETS[i]) : Lang.t("직접 입력"));
            group.add(choices[i]);
            presets.add(choices[i]);
            if (i < PRESETS.length) presets.add(Box.createHorizontalStrut(6));
        }
        body.add(presets);
        body.add(Box.createVerticalStrut(10));

        JTextField custom = new JTextField();
        custom.setFont(Ui.font(16, Font.PLAIN, false, 0));
        custom.setBackground(Ui.VOID);
        custom.setForeground(Ui.INK);
        custom.setCaretColor(Ui.AMBER);
        custom.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Ui.LINE), BorderFactory.createEmptyBorder(8, 10, 8, 10)));
        custom.setAlignmentX(Component.LEFT_ALIGNMENT);
        custom.setMaximumSize(new Dimension(Integer.MAX_VALUE, custom.getPreferredSize().height));
        custom.setEnabled(false);
        body.add(custom);
        body.add(Box.createVerticalStrut(10));

        JLabel status = label(" ", Ui.font(14, Font.BOLD, false, 0), Ui.AMBER);
        body.add(status);
        body.add(Box.createVerticalStrut(14));

        // QR 자리: 링크가 있고 금액이 맞을 때만 QR을 보여 준다
        JLabel qr = new JLabel("", SwingConstants.CENTER);
        qr.setFont(Ui.font(14, Ui.MEDIUM, false, 0));
        qr.setForeground(Ui.SOFT_INK);
        qr.setOpaque(true);
        qr.setBackground(Ui.VOID);
        qr.setBorder(BorderFactory.createLineBorder(Ui.LINE));
        qr.setAlignmentX(Component.LEFT_ALIGNMENT);
        Dimension qrBox = new Dimension(Integer.MAX_VALUE, 236);
        qr.setPreferredSize(new Dimension(10, 236));
        qr.setMaximumSize(qrBox);
        ImageIcon qrIcon = ready() ? new ImageIcon(QrCode.encode(payUrl()).toImage(6, Color.BLACK, Color.WHITE)) : null;
        body.add(qr);
        body.add(Box.createVerticalStrut(8));
        JLabel hint = label("<html>" + Lang.t("휴대폰 카메라나 카카오톡으로 QR을 찍으면 카카오페이 송금 화면이 열려요.") + "</html>",
                Ui.font(13, Font.PLAIN, false, 0), Ui.SOFT_INK);
        body.add(hint);

        JButton copy = button(Lang.t("링크 복사"), true), close = button(Lang.t("닫기"), false);

        // 지금 고른 금액 (-1 = 아직 고르지 않음)
        int[] amount = {PRESETS[0]};
        Runnable refresh = () -> {
            String problem = check(amount[0]);
            if (problem != null) {
                status.setText(amount[0] == -1 && custom.getText().isBlank() ? Lang.t("금액을 적어 주세요.") : problem);
                status.setForeground(Ui.DANGER);
            } else {
                status.setText(Lang.f("송금 화면에서 %,d원을 입력해 주세요.", amount[0]));
                status.setForeground(Ui.AMBER);
            }
            boolean show = ready() && problem == null;
            qr.setIcon(show ? qrIcon : null);
            qr.setText(show ? "" : Lang.t(ready() ? "금액을 먼저 골라 주세요" : "후원 링크 준비 중"));
            copy.setEnabled(show);
        };
        for (int i = 0; i <= PRESETS.length; i++) {
            final int index = i;
            choices[i].addActionListener(e -> {
                boolean own = index == PRESETS.length;
                custom.setEnabled(own);
                amount[0] = own ? parseAmount(custom.getText()) : PRESETS[index];
                if (own) custom.requestFocusInWindow();
                refresh.run();
            });
        }
        custom.getDocument().addDocumentListener(new DocumentListener() {
            private void changed() {
                amount[0] = parseAmount(custom.getText());
                refresh.run();
            }
            @Override public void insertUpdate(DocumentEvent e) { changed(); }
            @Override public void removeUpdate(DocumentEvent e) { changed(); }
            @Override public void changedUpdate(DocumentEvent e) { changed(); }
        });
        choices[0].setSelected(true);
        refresh.run();

        copy.addActionListener(e -> {
            if (check(amount[0]) != null || !ready()) return;     // 버튼이 꺼져 있어야 하지만 한 번 더 확인
            try {
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(payUrl()), null);
                status.setText(Lang.t("링크를 복사했어요. 휴대폰으로 보내서 열어 주세요."));
                status.setForeground(Ui.AMBER);
            } catch (Exception ex) {
                status.setText(Lang.t("복사하지 못했어요: ") + payUrl());
                status.setForeground(Ui.DANGER);
            }
        });
        close.addActionListener(e -> dialog.dispose());

        JPanel buttons = row();
        buttons.setBorder(BorderFactory.createEmptyBorder(0, 24, 22, 24));
        buttons.add(copy);
        buttons.add(Box.createHorizontalGlue());
        buttons.add(close);

        dialog.getContentPane().setBackground(Ui.PANEL);
        dialog.add(body, BorderLayout.CENTER);
        dialog.add(buttons, BorderLayout.SOUTH);
        dialog.pack();
        dialog.setSize(480, dialog.getHeight());
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
    }

    // ------------------------------------------------------------ 게임과 어울리는 어두운 모양의 부품들
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

    /** 금액 고르는 버튼: 고르면 노랗게 */
    private static JToggleButton choice(String text) {
        JToggleButton b = new JToggleButton(text);
        b.setFont(Ui.font(14, Ui.MEDIUM, false, 0));
        b.setFocusPainted(false);
        b.setContentAreaFilled(false);
        b.setOpaque(true);
        b.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Ui.LINE), BorderFactory.createEmptyBorder(9, 12, 9, 12)));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        Runnable paint = () -> {
            b.setBackground(b.isSelected() ? Ui.AMBER : Ui.VOID);
            b.setForeground(b.isSelected() ? Ui.DARK_INK : Ui.INK);
        };
        b.addItemListener(e -> paint.run());
        paint.run();
        return b;
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
                BorderFactory.createEmptyBorder(9, 16, 9, 16)));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }
}
