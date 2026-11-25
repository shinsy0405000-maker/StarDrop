import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Window;
import java.util.function.Consumer;

/**
 * 언어 고르는 창: 한국어 / English / 日本語 / 中文.
 *
 * 각 언어 이름은 그 언어의 글꼴로 쓴다 (한국어 화면에서도 日本語, 中文 이 네모로 깨지지 않게).
 * 하나를 누르면 onPick 에 그 언어를 넘기고 창을 닫는다.
 */
final class LanguagePicker {
    private LanguagePicker() { }

    static void open(Component parent, Consumer<Lang.Code> onPick) {
        Window owner = SwingUtilities.getWindowAncestor(parent);
        JDialog dialog = new JDialog(owner, Lang.t("언어") + " · Language", JDialog.ModalityType.APPLICATION_MODAL);

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBackground(Ui.PANEL);
        body.setBorder(BorderFactory.createEmptyBorder(22, 24, 22, 24));
        body.add(label(Lang.t("언어") + (Lang.current() == Lang.Code.EN ? "" : " · Language"), Ui.display(26, 0), Ui.INK));
        body.add(Box.createVerticalStrut(6));
        body.add(label(Lang.t("게임에서 쓸 언어를 고르세요."), Ui.font(13, Font.PLAIN, false, 0), Ui.DIM));
        body.add(Box.createVerticalStrut(16));

        // 언어 버튼 4개 (지금 언어는 노랗게)
        for (Lang.Code code : Lang.Code.values()) {
            boolean now = code == Lang.current();
            JButton b = new JButton(code.nativeName);
            b.setFont(Ui.fontFor(code, 17, now ? Font.BOLD : Font.PLAIN));
            b.setHorizontalAlignment(JButton.LEFT);
            b.setFocusPainted(false);
            b.setContentAreaFilled(false);
            b.setOpaque(true);
            b.setBackground(now ? Ui.AMBER : Ui.VOID);
            b.setForeground(now ? Ui.DARK_INK : Ui.INK);
            b.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(now ? Ui.AMBER : Ui.LINE),
                    BorderFactory.createEmptyBorder(10, 16, 10, 16)));
            b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            b.setAlignmentX(Component.LEFT_ALIGNMENT);
            b.setMaximumSize(new Dimension(Integer.MAX_VALUE, b.getPreferredSize().height));
            b.addActionListener(e -> {
                dialog.dispose();
                if (code != Lang.current()) onPick.accept(code);
            });
            body.add(b);
            body.add(Box.createVerticalStrut(6));
        }

        dialog.getContentPane().setBackground(Ui.PANEL);
        dialog.add(body, BorderLayout.CENTER);
        dialog.pack();
        dialog.setSize(360, dialog.getHeight());
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
    }

    private static JLabel label(String text, Font font, java.awt.Color color) {
        JLabel l = new JLabel(text);
        l.setFont(font);
        l.setForeground(color);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }
}
