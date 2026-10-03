package net.hypercubemc.iris_installer.shaders;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import net.hypercubemc.iris_installer.DarkModeDetector;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.WindowConstants;

/**
 * Lists the shader packs in an installation that the installer knows, for
 * making MCME edits of them: a tick box for each one it can edit, a download
 * button for each one whose version it can't - the version it can.
 */
@SuppressWarnings("serial")
public class ShaderPacksDialog extends JDialog {

    private final Path shaderpacks;
    private final JPanel list = new JPanel();
    private final JLabel status = new JLabel(" ");
    private final JButton refreshButton = new JButton("Refresh");
    private final JButton createButton = new JButton("Create MCME edits");
    private final Map<ShaderPacks.Pack, JCheckBox> ticks = new LinkedHashMap<>();
    private ShaderPackPatcher.Eye eye;
    private boolean busy;

    public ShaderPacksDialog(Window owner, Path gameDir) {
        super(owner, "MCME Shader Packs", ModalityType.MODELESS);
        this.shaderpacks = gameDir.resolve("shaderpacks");
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        if (owner != null) {
            setIconImages(owner.getIconImages());
        }

        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setBorder(BorderFactory.createEmptyBorder(20, 20, 16, 20));

        JLabel title = new JLabel("Shader packs");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        JLabel explanation = new JLabel("<html>Shader packs need an MCME edit to show Mordor's fire eye. Tick the packs to make one of: "
                + "a copy saved next to the pack as <i>name</i>" + ShaderPacks.SUFFIX + ", with the pack's settings. "
                + "The download button gets a version of a pack that can be edited.</html>");
        explanation.setFont(explanation.getFont().deriveFont(13f));
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        explanation.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.add(title);
        header.add(Box.createVerticalStrut(6));
        header.add(explanation);
        content.add(header, BorderLayout.NORTH);

        list.setLayout(new GridBagLayout());
        JPanel listHolder = new JPanel(new BorderLayout());
        listHolder.add(list, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(listHolder);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setBorder(BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor")));
        content.add(scroll, BorderLayout.CENTER);

        status.setFont(status.getFont().deriveFont(12f));
        status.setForeground(muted());
        refreshButton.addActionListener(e -> refresh());
        createButton.putClientProperty("JButton.buttonType", "roundRect");
        createButton.addActionListener(e -> createEdits());
        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> dispose());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(closeButton);
        buttons.add(createButton);
        JPanel footer = new JPanel(new BorderLayout(0, 10));
        footer.add(status, BorderLayout.NORTH);
        footer.add(refreshButton, BorderLayout.WEST);
        footer.add(buttons, BorderLayout.EAST);
        content.add(footer, BorderLayout.SOUTH);

        setContentPane(content);
        getRootPane().setDefaultButton(createButton);
        setMinimumSize(new Dimension(460, 420));
        setSize(new Dimension(540, 520));
        setLocationRelativeTo(owner);
    }

    /** Opens the window for gameDir's shader packs, and looks for them. */
    public static ShaderPacksDialog open(Window owner, Path gameDir) {
        ShaderPacksDialog dialog = new ShaderPacksDialog(owner, gameDir);
        dialog.setVisible(true);
        dialog.refresh();
        return dialog;
    }

    private static Color muted() {
        Color c = UIManager.getColor("Label.disabledForeground");
        return c != null ? c : Color.GRAY;
    }

    private void setBusy(boolean busy, String message) {
        this.busy = busy;
        refreshButton.setEnabled(!busy);
        updateCreateButton();
        for (JCheckBox tick : ticks.values()) {
            tick.setEnabled(!busy);
        }
        status.setText(message);
    }

    private void updateCreateButton() {
        boolean any = false;
        for (JCheckBox tick : ticks.values()) {
            any |= tick.isSelected();
        }
        createButton.setEnabled(!busy && any);
    }

    // ---------------------------------------------------------------- the list

    /** Looks for the shader packs again, trying each with the recipes. */
    public void refresh() {
        if (busy) {
            return;
        }
        showMessage("Looking for shader packs…");
        setBusy(true, " ");
        new SwingWorker<List<ShaderPacks.Pack>, Void>() {
            @Override
            protected List<ShaderPacks.Pack> doInBackground() throws Exception {
                if (eye == null) {
                    eye = ShaderPackPatcher.Eye.bundled();
                }
                return ShaderPacks.scan(shaderpacks, eye);
            }

            @Override
            protected void done() {
                List<ShaderPacks.Pack> packs;
                try {
                    packs = get();
                } catch (InterruptedException | ExecutionException e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    cause.printStackTrace();
                    ticks.clear();
                    setBusy(false, " ");
                    showMessage("Couldn't read " + shaderpacks + ": " + cause.getMessage());
                    return;
                }
                showPacks(packs);
            }
        }.execute();
    }

    private void showMessage(String message) {
        list.removeAll();
        ticks.clear();
        JLabel label = new JLabel("<html><div style='text-align:center'>" + escape(message) + "</div></html>", SwingConstants.CENTER);
        label.setForeground(muted());
        GridBagConstraints c = new GridBagConstraints();
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(40, 20, 40, 20);
        list.add(label, c);
        list.revalidate();
        list.repaint();
    }

    private void showPacks(List<ShaderPacks.Pack> packs) {
        list.removeAll();
        ticks.clear();
        if (packs.isEmpty()) {
            setBusy(false, " ");
            showMessage("No shader packs that can be edited are in " + shaderpacks + ".\n\nSupported: "
                    + String.join(", ", ShaderPacks.familyNames()) + ".");
            return;
        }
        int row = 0;
        for (ShaderPacks.Pack pack : packs) {
            addRow(pack, row++);
        }
        int supported = ticks.size();
        setBusy(false, supported == packs.size() ? " "
                : (packs.size() - supported) + " of these can't be edited in the version you have - download one that can.");
        list.revalidate();
        list.repaint();
    }

    private void addRow(ShaderPacks.Pack pack, int row) {
        JPanel line = new JPanel(new BorderLayout(12, 0));
        line.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(row == 0 ? 0 : 1, 0, 0, 0, UIManager.getColor("Component.borderColor")),
                BorderFactory.createEmptyBorder(10, 14, 10, 12)));

        JLabel name = new JLabel(pack.displayName());
        name.setFont(name.getFont().deriveFont(Font.BOLD, 14f));
        JLabel detail = new JLabel(detail(pack));
        detail.setFont(detail.getFont().deriveFont(12f));
        detail.setForeground(muted());
        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        text.add(name);
        text.add(Box.createVerticalStrut(2));
        text.add(detail);
        line.add(text, BorderLayout.CENTER);

        JComponent action;
        if (pack.supported()) {
            JCheckBox tick = new JCheckBox();
            tick.setSelected(true);
            tick.setToolTipText("Make an MCME edit of " + pack.displayName());
            tick.addItemListener(e -> updateCreateButton());
            ticks.put(pack, tick);
            name.setLabelFor(tick);
            action = tick;
        } else {
            action = downloadButton(pack);
        }
        JPanel actionHolder = new JPanel(new GridBagLayout());
        actionHolder.setOpaque(false);
        actionHolder.setPreferredSize(new Dimension(44, 32));
        actionHolder.add(action);
        line.add(actionHolder, BorderLayout.EAST);

        GridBagConstraints c = new GridBagConstraints();
        c.gridy = row;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        list.add(line, c);
    }

    private static String detail(ShaderPacks.Pack pack) {
        if (!pack.supported()) {
            return pack.family.name + " · this version can't be edited — " + pack.family.version + " can";
        }
        if (pack.edited()) {
            return pack.family.name + " · has an MCME edit, which will be remade";
        }
        if (pack.editTakenByOther()) {
            return pack.family.name + " · " + pack.edit().getFileName() + " is already there";
        }
        return pack.family.name + " · ready to edit";
    }

    private JButton downloadButton(ShaderPacks.Pack pack) {
        ShaderPacks.Download download = pack.download();
        JButton button = new JButton(new DownloadIcon());
        button.putClientProperty("JButton.buttonType", "toolBarButton");
        button.setMargin(new Insets(4, 4, 4, 4));
        boolean have = Files.exists(shaderpacks.resolve(download.fileName));
        button.setEnabled(!have);
        button.setToolTipText(have ? download.fileName + " is already in your shader packs"
                : "Download " + pack.family.name + " " + pack.family.version + " (" + download.fileName + ") from Modrinth");
        button.getAccessibleContext().setAccessibleName("Download " + pack.family.name + " " + pack.family.version);
        button.addActionListener(e -> download(pack, button));
        return button;
    }

    private void download(ShaderPacks.Pack pack, JButton button) {
        if (busy) {
            return;
        }
        ShaderPacks.Download download = pack.download();
        setBusy(true, "Downloading " + download.fileName + "…");
        button.setEnabled(false);
        new SwingWorker<Path, Integer>() {
            @Override
            protected Path doInBackground() throws Exception {
                return ShaderPacks.download(download, shaderpacks, this::publish);
            }

            @Override
            protected void process(List<Integer> chunks) {
                status.setText("Downloading " + download.fileName + "… " + chunks.get(chunks.size() - 1) + "%");
            }

            @Override
            protected void done() {
                try {
                    get();
                    setBusy(false, " ");
                    refresh();
                } catch (InterruptedException | ExecutionException e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    cause.printStackTrace();
                    button.setEnabled(true);
                    setBusy(false, "Download failed: " + cause.getMessage());
                    JOptionPane.showMessageDialog(ShaderPacksDialog.this,
                            "Couldn't download " + download.fileName + ":\n" + cause.getMessage()
                                    + "\n\nYou can get it from " + pack.family.page + " and put it in\n" + shaderpacks,
                            "Download failed", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    // ---------------------------------------------------------------- making edits

    private void createEdits() {
        if (busy) {
            return;
        }
        List<ShaderPacks.Pack> chosen = new ArrayList<>();
        List<String> taken = new ArrayList<>();
        for (Map.Entry<ShaderPacks.Pack, JCheckBox> tick : ticks.entrySet()) {
            if (tick.getValue().isSelected()) {
                chosen.add(tick.getKey());
                if (tick.getKey().editTakenByOther()) {
                    taken.add(tick.getKey().edit().getFileName().toString());
                }
            }
        }
        if (chosen.isEmpty()) {
            return;
        }
        if (!taken.isEmpty()) {
            int answer = JOptionPane.showConfirmDialog(this,
                    "These are already in your shader packs, but weren't made by the installer:\n\n  "
                            + String.join("\n  ", taken) + "\n\nReplace them with new MCME edits?",
                    "Replace shader packs?", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (answer != JOptionPane.YES_OPTION) {
                return;
            }
        }

        setBusy(true, "Creating MCME edits…");
        new SwingWorker<List<String>, String>() {
            @Override
            protected List<String> doInBackground() {
                List<String> failures = new ArrayList<>();
                for (ShaderPacks.Pack pack : chosen) {
                    publish(pack.displayName());
                    try {
                        ShaderPacks.makeEdit(pack, eye);
                    } catch (ShaderPackPatcher.NotSupported e) {
                        failures.add(pack.displayName() + ": this version can't be edited");
                    } catch (IOException | RuntimeException e) {
                        e.printStackTrace();
                        failures.add(pack.displayName() + ": " + e.getMessage());
                    }
                }
                return failures;
            }

            @Override
            protected void process(List<String> names) {
                status.setText("Editing " + names.get(names.size() - 1) + "…");
            }

            @Override
            protected void done() {
                List<String> failures;
                try {
                    failures = get();
                } catch (InterruptedException | ExecutionException e) {
                    failures = new ArrayList<>();
                    failures.add(String.valueOf(e.getCause() != null ? e.getCause().getMessage() : e.getMessage()));
                }
                int made = chosen.size() - failures.size();
                setBusy(false, " ");
                refresh();
                if (failures.isEmpty()) {
                    JOptionPane.showMessageDialog(ShaderPacksDialog.this,
                            (made == 1 ? "The MCME edit is" : "The " + made + " MCME edits are") + " in your shader packs, named "
                                    + "with " + ShaderPacks.SUFFIX + ".\nPick one in Iris' shader pack menu (Options > Video Settings > Shader Packs).",
                            "MCME edits created", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(ShaderPacksDialog.this,
                            (made > 0 ? made + " MCME edit" + (made == 1 ? " was" : "s were") + " created, but not all:\n\n" : "No MCME edits were created:\n\n")
                                    + String.join("\n", failures) + "\n\nMake sure Minecraft is closed and try again.",
                            "Some edits failed", JOptionPane.WARNING_MESSAGE);
                }
            }
        }.execute();
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>");
    }

    /** An arrow down into a tray, in the button's text colour. */
    private static final class DownloadIcon implements Icon {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            Color color = UIManager.getColor(c.isEnabled() ? "Button.foreground" : "Button.disabledText");
            g2.setColor(color != null ? color : c.getForeground());
            g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.translate(x, y);
            g2.drawLine(10, 2, 10, 13);
            g2.drawLine(5, 9, 10, 14);
            g2.drawLine(15, 9, 10, 14);
            g2.drawLine(3, 14, 3, 17);
            g2.drawLine(3, 17, 17, 17);
            g2.drawLine(17, 17, 17, 14);
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return 20;
        }

        @Override
        public int getIconHeight() {
            return 20;
        }
    }

    /**
     * Opens the window on its own, for a game folder (default: .minecraft).
     *
     *     java -cp MCME-Installer.jar net.hypercubemc.iris_installer.shaders.ShaderPacksDialog [game folder]
     */
    public static void main(String[] args) {
        if (DarkModeDetector.isDarkMode()) {
            FlatDarkLaf.setup();
        } else {
            FlatLightLaf.setup();
        }
        Path gameDir = args.length > 0 ? Paths.get(args[0])
                : Paths.get(System.getenv("APPDATA") != null ? System.getenv("APPDATA") : System.getProperty("user.home"), ".minecraft");
        SwingUtilities.invokeLater(() -> open(null, gameDir).addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                System.exit(0);
            }
        }));
    }
}
