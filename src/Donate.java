import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Window;
import java.net.URI;

/**
 * 후원 창.
 *
 * 금액(10,000원 / 30,000원 / 50,000원 / 직접 입력)을 고르면
 * 토스 송금 링크(toss.me/아이디/금액)를 브라우저로 열어 준다. 10,000원보다 적은 금액은 고를 수 없다.
 * 토스 아이디가 아직 없으면 TOSS_ID 를 비워 두고, 생기면 이 한 줄만 바꾸면 된다.
 */
final class Donate {
    private Donate() { }

    /** 토스 아이디 (toss.me/ 뒤에 붙는 부분). 비어 있으면 "준비 중"으로 보인다 */
    static final String TOSS_ID = "";

    static final int MIN_AMOUNT = 10_000;          // 최소 후원 금액(원)
    static final int MAX_AMOUNT = 10_000_000;      // 잘못 입력한 큰 숫자를 막기 위한 한도
    static final int[] PRESETS = {10_000, 30_000, 50_000};

    static boolean ready() {
        return !TOSS_ID.isBlank();
    }

    /** 토스 송금 주소. 금액이 미리 채워진 채로 열린다 */
    static String tossUrl(int amount) {
        return "https://toss.me/" + TOSS_ID.strip() + "/" + amount;
    }

    /** "12,000" 이나 "12000원" 같은 글에서 금액을 읽는다. 숫자가 없으면 -1 */
    static int parseAmount(String text) {
        String digits = text.replaceAll("[^0-9]", "");
        if (digits.isEmpty() || digits.length() > 9) return -1;
        return Integer.parseInt(digits);
    }

    /** 이 금액으로 후원할 수 있으면 null, 안 되면 이유 */
    static String check(int amount) {
        if (amount < 0) return "금액을 숫자로 적어 주세요.";
        if (amount < MIN_AMOUNT) return String.format("최소 %,d원부터 후원할 수 있어요.", MIN_AMOUNT);
        if (amount > MAX_AMOUNT) return String.format("한 번에 %,d원까지 후원할 수 있어요.", MAX_AMOUNT);
        return null;
    }

    static void open(Component parent) {
        Window owner = SwingUtilities.getWindowAncestor(parent);
        JDialog dialog = new JDialog(owner, "후원하기", JDialog.ModalityType.APPLICATION_MODAL);

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBackground(Ui.PANEL);
        body.setBorder(BorderFactory.createEmptyBorder(22, 24, 18, 24));
        body.add(label("후원하기", Ui.display(26, 0), Ui.INK));
        body.add(Box.createVerticalStrut(8));
        body.add(label("<html>별똥 택배가 재미있었다면 후원으로 응원해 주세요.<br>후원금은 새 구역과 기능을 만드는 데 쓰입니다.</html>",
                Ui.font(14, Font.PLAIN, false, 0), Ui.SOFT_INK));
        body.add(Box.createVerticalStrut(18));
        body.add(label("금액 (최소 10,000원)", Ui.font(13, Font.BOLD, false, 0), Ui.SOFT_INK));
        body.add(Box.createVerticalStrut(8));

        // 금액 고르기: 정해진 금액 3개 + 직접 입력
        JPanel presets = row();
        ButtonGroup group = new ButtonGroup();
        JToggleButton[] choices = new JToggleButton[PRESETS.length + 1];
        for (int i = 0; i <= PRESETS.length; i++) {
            choices[i] = choice(i < PRESETS.length ? String.format("%,d원", PRESETS[i]) : "직접 입력");
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

        JButton send = button("토스로 후원하기", true), close = button("닫기", false);
        if (!ready()) {
            send.setText("후원 링크 준비 중");
            send.setEnabled(false);
        }

        // 지금 고른 금액 (-1 = 아직 고르지 않음)
        int[] amount = {PRESETS[0]};
        Runnable refresh = () -> {
            String problem = check(amount[0]);
            if (problem != null) {
                status.setText(amount[0] == -1 && custom.getText().isBlank() ? "금액을 적어 주세요." : problem);
                status.setForeground(Ui.DANGER);
            } else {
                status.setText(String.format("%,d원을 후원합니다.", amount[0]));
                status.setForeground(Ui.AMBER);
            }
            send.setEnabled(ready() && problem == null);
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

        send.addActionListener(e -> {
            if (check(amount[0]) != null || !ready()) return;     // 버튼이 꺼져 있어야 하지만 한 번 더 확인
            try {
                Desktop.getDesktop().browse(new URI(tossUrl(amount[0])));
                dialog.dispose();
            } catch (Exception ex) {
                status.setText("브라우저를 열지 못했어요. " + tossUrl(amount[0]));
                status.setForeground(Ui.DANGER);
            }
        });
        close.addActionListener(e -> dialog.dispose());

        JPanel buttons = row();
        buttons.setBorder(BorderFactory.createEmptyBorder(0, 24, 22, 24));
        buttons.add(send);
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
