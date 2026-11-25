import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.StringSelection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 버그 제보 창.
 *
 * 무엇이 잘못됐는지, 어떻게 하면 다시 생기는지 적으면
 * 버전, 구역, 운영체제 같은 정보를 붙여서 GitHub 의 "버그 제보" 양식을 미리 채운 채로 열어 준다.
 * (GitHub 쪽 양식은 .github/ISSUE_TEMPLATE/bug_report.yml)
 */
final class BugReport {
    private BugReport() { }

    static final String REPO = "https://github.com/shinsy0405000-maker/StarDrop";

    /** 창을 띄운다. info = 게임이 자동으로 채워 주는 정보(버전, 구역 등) */
    static void open(Component parent, String info) {
        Window owner = SwingUtilities.getWindowAncestor(parent);
        JDialog dialog = new JDialog(owner, Lang.t("버그 제보"), JDialog.ModalityType.APPLICATION_MODAL);

        JTextArea what = area(5, false), steps = area(4, false), env = area(6, true);
        steps.setText("1. \n2. \n3. ");
        env.setText(info);

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBackground(Ui.PANEL);
        body.setBorder(BorderFactory.createEmptyBorder(22, 24, 18, 24));
        body.add(label(Lang.t("버그 제보"), Ui.display(26, 0), Ui.INK));
        body.add(Box.createVerticalStrut(6));
        body.add(label(Lang.t("이상한 점을 알려 주세요. GitHub 제보 양식이 미리 채워진 채로 열립니다."), Ui.font(13, Font.PLAIN, false, 0), Ui.DIM));
        body.add(Box.createVerticalStrut(16));
        body.add(field(Lang.t("무엇이 잘못됐나요?"), what));
        body.add(Box.createVerticalStrut(12));
        body.add(field(Lang.t("어떻게 하면 다시 생기나요?"), steps));
        body.add(Box.createVerticalStrut(12));
        body.add(field(Lang.t("자동으로 붙는 정보"), env));

        JButton send = button(Lang.t("GitHub에 제보하기"), true), copy = button(Lang.t("내용 복사"), false), close = button(Lang.t("닫기"), false);
        send.addActionListener(e -> {
            if (what.getText().isBlank()) {
                JOptionPane.showMessageDialog(dialog, Lang.t("무엇이 잘못됐는지 한 줄이라도 적어 주세요."), Lang.t("버그 제보"), JOptionPane.INFORMATION_MESSAGE);
                what.requestFocusInWindow();
                return;
            }
            String url = issueUrl(what.getText(), steps.getText(), env.getText());
            if (browse(url)) {
                dialog.dispose();
            } else {                    // 브라우저를 못 열면 주소를 복사해 준다
                copyToClipboard(url);
                JOptionPane.showMessageDialog(dialog, Lang.t("브라우저를 열지 못해서 제보 주소를 복사했습니다.\n브라우저 주소창에 붙여 넣어 주세요."), Lang.t("버그 제보"), JOptionPane.INFORMATION_MESSAGE);
            }
        });
        copy.addActionListener(e -> {
            copyToClipboard("무엇이 잘못됐나요?\n" + what.getText() + "\n\n어떻게 하면 다시 생기나요?\n" + steps.getText() + "\n\n환경\n" + env.getText());
            copy.setText(Lang.t("복사했습니다"));
        });
        close.addActionListener(e -> dialog.dispose());

        JPanel buttons = new JPanel();
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
        buttons.setBackground(Ui.PANEL);
        buttons.setBorder(BorderFactory.createEmptyBorder(0, 24, 22, 24));
        buttons.add(send);
        buttons.add(Box.createHorizontalStrut(8));
        buttons.add(copy);
        buttons.add(Box.createHorizontalGlue());
        buttons.add(close);

        dialog.getContentPane().setBackground(Ui.PANEL);
        dialog.add(body, BorderLayout.CENTER);
        dialog.add(buttons, BorderLayout.SOUTH);
        dialog.pack();                                         // 내용에 맞는 높이로
        dialog.setSize(560, dialog.getHeight());
        dialog.setLocationRelativeTo(owner);
        SwingUtilities.invokeLater(what::requestFocusInWindow);
        dialog.setVisible(true);            // 창을 닫을 때까지 여기서 기다린다
    }

    /** GitHub "새 이슈" 주소. 양식(bug_report.yml)의 칸 이름(what, steps, env)으로 내용을 미리 채운다 */
    static String issueUrl(String what, String steps, String env) {
        String first = what.strip().lines().findFirst().orElse("");
        String title = "[버그] " + (first.length() > 40 ? first.substring(0, 40) + "…" : first);
        return REPO + "/issues/new?template=bug_report.yml"
                + "&title=" + encode(title) + "&what=" + encode(what) + "&steps=" + encode(steps) + "&env=" + encode(env);
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static boolean browse(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
                return true;
            }
        } catch (Exception e) {
            // 아래에서 false
        }
        return false;
    }

    private static void copyToClipboard(String s) {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(s), null);
    }

    // ------------------------------------------------------------ 게임과 어울리는 어두운 모양의 부품들
    private static JTextArea area(int rows, boolean readOnly) {
        JTextArea a = new JTextArea(rows, 30);
        a.setLineWrap(true);
        a.setWrapStyleWord(true);
        a.setEditable(!readOnly);
        a.setFont(Ui.font(readOnly ? 13 : 14, Font.PLAIN, false, 0));     // 고정폭 글꼴에는 한글이 없어서 본문 글꼴을 쓴다
        a.setBackground(readOnly ? new Color(10, 11, 30) : Ui.VOID);
        a.setForeground(readOnly ? Ui.DIM : Ui.INK);
        a.setCaretColor(Ui.AMBER);
        a.setSelectionColor(new Color(255, 198, 92, 90));
        a.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        return a;
    }

    private static JComponent field(String title, JTextArea area) {
        JPanel p = new JPanel(new BorderLayout(0, 6));
        p.setBackground(Ui.PANEL);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(label(title, Ui.font(13, Font.BOLD, false, 0), Ui.SOFT_INK), BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(area, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createLineBorder(Ui.LINE));
        scroll.getViewport().setBackground(area.getBackground());
        p.add(scroll, BorderLayout.CENTER);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, p.getPreferredSize().height));
        return p;
    }

    private static JLabel label(String text, Font font, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(font);
        l.setForeground(color);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
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
