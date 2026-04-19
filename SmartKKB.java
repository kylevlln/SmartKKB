import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.io.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

public class SmartKKB extends JFrame {

    // --- DATABASE ---
    private static final Map<String, ExpenseGroup> database = new HashMap<>();
    private static final Map<String, ExpenseGroup> deletedDatabase = new HashMap<>(); // For deleted groups

    // File where the data will be saved
    private static final String DATA_FILE = "smartkkb_data.dat";

    // --- UI COMPONENTS ---
    private CardLayout cardLayout;
    private JPanel mainPanel;
    private ExpenseGroup currentGroup;

    private JPanel homeGroupsPanel;
    private JPanel deletedGroupsPanel;
    private JLabel groupHeaderLabel;
    private JLabel groupSubHeaderLabel;
    private JPanel expensesListPanel;
    private JPanel membersFlowPanel;
    private JLabel membersCountLabel;
    private SearchField searchBar;

    // --- ORIGINAL GREYISH THEME COLORS ---
    private static final Color BG_APP = new Color(238, 240, 242);
    private static final Color BG_CARD = new Color(255, 255, 255);
    private static final Color ACCENT = new Color(85, 92, 100);
    private static final Color ACCENT_HOVER = new Color(65, 70, 78);
    private static final Color DANGER = new Color(200, 75, 75);
    private static final Color TEXT_MAIN = new Color(40, 44, 50);
    private static final Color TEXT_MUTED = new Color(130, 135, 142);
    private static final Color BORDER_COLOR = new Color(215, 220, 225);

    public SmartKKB() {
        setTitle("SmartKKB");
        setSize(1000, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        getContentPane().setBackground(BG_APP);

        // 1. LOAD SAVED DATA WHEN APP STARTS
        loadData();

        // 2. SAVE DATA WHEN APP CLOSES
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                saveData();
            }
        });

        cardLayout = new CardLayout();
        mainPanel = new JPanel(cardLayout);
        mainPanel.setOpaque(false);

        mainPanel.add(createHomeScreen(), "HOME");
        mainPanel.add(createGroupingsScreen(), "GROUPINGS");
        mainPanel.add(createGroupScreen(), "GROUP_VIEW");
        mainPanel.add(createDeletedGroupsScreen(), "DELETED_GROUPS");

        add(mainPanel);
        cardLayout.show(mainPanel, "HOME");
    }

    // ==========================================
    // DATA PERSISTENCE (SAVE / LOAD)
    // ==========================================
    private void saveData() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(DATA_FILE))) {
            oos.writeObject(database);
            oos.writeObject(deletedDatabase);
            System.out.println("Data saved successfully.");
        } catch (IOException e) {
            System.err.println("Error saving data: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void loadData() {
        File file = new File(DATA_FILE);
        if (file.exists()) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
                Map<String, ExpenseGroup> loadedDb = (Map<String, ExpenseGroup>) ois.readObject();
                Map<String, ExpenseGroup> loadedDeletedDb = (Map<String, ExpenseGroup>) ois.readObject();

                database.clear();
                database.putAll(loadedDb);

                deletedDatabase.clear();
                deletedDatabase.putAll(loadedDeletedDb);

                System.out.println("Data loaded successfully.");
            } catch (Exception e) {
                System.err.println("Error loading data: " + e.getMessage());
            }
        }
    }

    // ==========================================
    // SCREEN 1: HOME (MAIN MENU - YOUR GROUPS)
    // ==========================================
    private JPanel createHomeScreen() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BG_APP);

        // Top Navigation
        JPanel topNav = new JPanel(new BorderLayout());
        topNav.setOpaque(false);
        topNav.setBorder(new EmptyBorder(25, 30, 10, 30));

        JPanel leftNav = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 0));
        leftNav.setOpaque(false);

        JLabel menuLabel = new JLabel("My Groups");
        menuLabel.setFont(new Font("Segoe UI", Font.BOLD, 24));
        menuLabel.setForeground(TEXT_MAIN);

        ModernButton recentlyDeletedBtn = new ModernButton("Recently Deleted", BG_CARD, TEXT_MAIN, BORDER_COLOR);
        recentlyDeletedBtn.setPreferredSize(new Dimension(140, 35));
        recentlyDeletedBtn.addActionListener(e -> {
            refreshDeletedScreen();
            cardLayout.show(mainPanel, "DELETED_GROUPS");
        });

        leftNav.add(menuLabel);
        leftNav.add(recentlyDeletedBtn);

        JPanel rightNav = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 0));
        rightNav.setOpaque(false);

        searchBar = new SearchField("Search groups...");
        searchBar.setPreferredSize(new Dimension(250, 40));

        searchBar.getDocument().addDocumentListener(new DocumentListener() {
            private String lastQuery = "";

            public void insertUpdate(DocumentEvent e) { doSearch(); }
            public void removeUpdate(DocumentEvent e) { doSearch(); }
            public void changedUpdate(DocumentEvent e) { doSearch(); }

            private void doSearch() {
                String query = searchBar.getText();
                if (query.equals("Search groups...")) query = "";
                query = query.trim().toLowerCase();

                if (query.equals(lastQuery)) return;
                lastQuery = query;

                refreshHome(query);
            }
        });

        rightNav.add(searchBar);

        topNav.add(leftNav, BorderLayout.WEST);
        topNav.add(rightNav, BorderLayout.EAST);

        // Header Texts
        JPanel headerPanel = new JPanel();
        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));
        headerPanel.setOpaque(false);
        headerPanel.setBorder(new EmptyBorder(30, 0, 20, 0));

        JLabel welcomeLabel = new JLabel("SmartKKB Dashboard");
        welcomeLabel.setFont(new Font("Segoe UI", Font.BOLD, 32));
        welcomeLabel.setForeground(TEXT_MAIN);
        welcomeLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel subWelcome = new JLabel("Select a group below to manage expenses or split bills.");
        subWelcome.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        subWelcome.setForeground(TEXT_MUTED);
        subWelcome.setAlignmentX(Component.CENTER_ALIGNMENT);

        headerPanel.add(welcomeLabel);
        headerPanel.add(Box.createVerticalStrut(10));
        headerPanel.add(subWelcome);

        // Groups List
        homeGroupsPanel = new JPanel();
        homeGroupsPanel.setLayout(new BoxLayout(homeGroupsPanel, BoxLayout.Y_AXIS));
        homeGroupsPanel.setOpaque(false);

        JPanel listWrapper = new JPanel(new BorderLayout());
        listWrapper.setOpaque(false);
        listWrapper.add(homeGroupsPanel, BorderLayout.NORTH);

        JScrollPane scrollPane = new JScrollPane(listWrapper);
        scrollPane.setBorder(null);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setOpaque(false);
        centerPanel.add(headerPanel, BorderLayout.NORTH);
        centerPanel.add(scrollPane, BorderLayout.CENTER);

        // Bottom Actions (Create / Join)
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 10));
        bottomPanel.setOpaque(false);
        bottomPanel.setBorder(new EmptyBorder(10, 30, 30, 30));

        ModernButton joinBtn = new ModernButton("Join via Code", BG_CARD, TEXT_MAIN, BORDER_COLOR);
        joinBtn.setPreferredSize(new Dimension(150, 45));
        joinBtn.addActionListener(e -> cardLayout.show(mainPanel, "GROUPINGS"));

        ModernButton fabBtn = new ModernButton("+ Create Group", ACCENT, Color.WHITE, ACCENT);
        fabBtn.setPreferredSize(new Dimension(160, 45));
        fabBtn.setFont(new Font("Segoe UI", Font.BOLD, 15));
        fabBtn.addActionListener(e -> createNewGroupFlow());

        bottomPanel.add(joinBtn);
        bottomPanel.add(fabBtn);

        panel.add(topNav, BorderLayout.NORTH);
        panel.add(centerPanel, BorderLayout.CENTER);
        panel.add(bottomPanel, BorderLayout.SOUTH);

        refreshHome("");
        return panel;
    }

    private void refreshHome(String searchQuery) {
        if (homeGroupsPanel == null) return;
        homeGroupsPanel.removeAll();

        if (database.isEmpty()) {
            JLabel emptyLabel = new JLabel("You haven't created or joined any groups yet.");
            emptyLabel.setFont(new Font("Segoe UI", Font.PLAIN, 16));
            emptyLabel.setForeground(TEXT_MUTED);
            emptyLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            homeGroupsPanel.add(emptyLabel);
        } else {
            boolean foundMatch = false;
            for (ExpenseGroup group : database.values()) {
                if (searchQuery.isEmpty() || group.name.toLowerCase().contains(searchQuery) ||
                        group.code.toLowerCase().contains(searchQuery) || group.category.toLowerCase().contains(searchQuery)) {

                    foundMatch = true;
                    JPanel cardWrapper = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 10));
                    cardWrapper.setOpaque(false);

                    GroupCard card = new GroupCard(group);

                    ModernButton openBtn = new ModernButton("Open", ACCENT, Color.WHITE, ACCENT);
                    openBtn.setPreferredSize(new Dimension(100, 40));
                    openBtn.setFont(new Font("Segoe UI", Font.BOLD, 14));
                    openBtn.addActionListener(e -> {
                        currentGroup = group;
                        refreshGroupScreen();
                        cardLayout.show(mainPanel, "GROUP_VIEW");
                    });

                    card.add(openBtn, BorderLayout.EAST);
                    cardWrapper.add(card);
                    homeGroupsPanel.add(cardWrapper);
                }
            }

            if (!foundMatch) {
                JLabel errorLabel = new JLabel("No results found for '" + searchQuery + "'.");
                errorLabel.setFont(new Font("Segoe UI", Font.PLAIN, 16));
                errorLabel.setForeground(DANGER);
                errorLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
                homeGroupsPanel.add(errorLabel);
            }
        }

        homeGroupsPanel.revalidate();
        homeGroupsPanel.repaint();
    }

    private void createNewGroupFlow() {
        String[] categories = {"Family", "Friends", "Classmates", "Partner", "Work", "Other"};
        String category = (String) JOptionPane.showInputDialog(this, "Select Group Category:",
                "New Group", JOptionPane.PLAIN_MESSAGE, null, categories, categories[0]);
        if (category == null) return;

        String code = generateUniqueCode();
        String name = JOptionPane.showInputDialog(this, "Your group code is: " + code + "\n\nEnter a Group Name:");
        if (name == null || name.trim().isEmpty()) {
            return;
        }

        ExpenseGroup newGroup = new ExpenseGroup(code, category, name.trim());
        database.put(code, newGroup);

        searchBar.resetPlaceholder();
        refreshHome("");
        currentGroup = newGroup;
        refreshGroupScreen();
        cardLayout.show(mainPanel, "GROUP_VIEW");
    }

    // ==========================================
    // SCREEN 1.5: RECENTLY DELETED GROUPS
    // ==========================================
    private JPanel createDeletedGroupsScreen() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BG_APP);

        JPanel topNav = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topNav.setOpaque(false);
        topNav.setBorder(new EmptyBorder(25, 30, 0, 30));

        JLabel backBtn = new JLabel("<html><b>&lt; Back</b></html>");
        backBtn.setFont(new Font("Segoe UI", Font.PLAIN, 18));
        backBtn.setForeground(TEXT_MAIN);
        backBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        backBtn.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                refreshHome("");
                cardLayout.show(mainPanel, "HOME");
            }
        });
        topNav.add(backBtn);

        JPanel headerPanel = new JPanel();
        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));
        headerPanel.setOpaque(false);
        headerPanel.setBorder(new EmptyBorder(20, 0, 20, 0));

        JLabel title = new JLabel("Recently Deleted Groups");
        title.setFont(new Font("Segoe UI", Font.BOLD, 32));
        title.setForeground(TEXT_MAIN);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        headerPanel.add(title);

        deletedGroupsPanel = new JPanel();
        deletedGroupsPanel.setLayout(new BoxLayout(deletedGroupsPanel, BoxLayout.Y_AXIS));
        deletedGroupsPanel.setOpaque(false);

        JPanel listWrapper = new JPanel(new BorderLayout());
        listWrapper.setOpaque(false);
        listWrapper.add(deletedGroupsPanel, BorderLayout.NORTH);

        JScrollPane scrollPane = new JScrollPane(listWrapper);
        scrollPane.setBorder(null);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        panel.add(topNav, BorderLayout.NORTH);

        JPanel centerWrapper = new JPanel(new BorderLayout());
        centerWrapper.setOpaque(false);
        centerWrapper.add(headerPanel, BorderLayout.NORTH);
        centerWrapper.add(scrollPane, BorderLayout.CENTER);
        panel.add(centerWrapper, BorderLayout.CENTER);

        return panel;
    }

    private void refreshDeletedScreen() {
        if (deletedGroupsPanel == null) return;
        deletedGroupsPanel.removeAll();

        if (deletedDatabase.isEmpty()) {
            JLabel emptyLabel = new JLabel("No recently deleted groups.");
            emptyLabel.setFont(new Font("Segoe UI", Font.PLAIN, 16));
            emptyLabel.setForeground(TEXT_MUTED);
            emptyLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            deletedGroupsPanel.add(emptyLabel);
        } else {
            for (ExpenseGroup group : deletedDatabase.values()) {
                JPanel cardWrapper = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 10));
                cardWrapper.setOpaque(false);

                GroupCard card = new GroupCard(group);

                JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
                btnPanel.setOpaque(false);

                ModernButton restoreBtn = new ModernButton("Restore", ACCENT, Color.WHITE, ACCENT);
                restoreBtn.setPreferredSize(new Dimension(100, 35));
                restoreBtn.addActionListener(e -> {
                    database.put(group.code, group);
                    deletedDatabase.remove(group.code);
                    refreshDeletedScreen();
                });

                ModernButton permDeleteBtn = new ModernButton("Delete", BG_APP, DANGER, BORDER_COLOR);
                permDeleteBtn.setPreferredSize(new Dimension(80, 35));
                permDeleteBtn.addActionListener(e -> {
                    int confirm = JOptionPane.showConfirmDialog(this, "Permanently delete this group? This cannot be undone.", "Confirm", JOptionPane.YES_NO_OPTION);
                    if (confirm == JOptionPane.YES_OPTION) {
                        deletedDatabase.remove(group.code);
                        refreshDeletedScreen();
                    }
                });

                btnPanel.add(restoreBtn);
                btnPanel.add(permDeleteBtn);

                card.add(btnPanel, BorderLayout.EAST);
                cardWrapper.add(card);
                deletedGroupsPanel.add(cardWrapper);
            }
        }

        deletedGroupsPanel.revalidate();
        deletedGroupsPanel.repaint();
    }


    // ==========================================
    // SCREEN 2: GROUPINGS (JOIN EXISTING)
    // ==========================================
    private JPanel createGroupingsScreen() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BG_APP);

        JPanel topNav = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topNav.setOpaque(false);
        topNav.setBorder(new EmptyBorder(25, 30, 0, 30));

        JLabel backBtn = new JLabel("<html><b>&lt; Back</b></html>");
        backBtn.setFont(new Font("Segoe UI", Font.PLAIN, 18));
        backBtn.setForeground(TEXT_MAIN);
        backBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        backBtn.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                refreshHome("");
                cardLayout.show(mainPanel, "HOME");
            }
        });
        topNav.add(backBtn);

        JPanel formContainer = new JPanel();
        formContainer.setLayout(new BoxLayout(formContainer, BoxLayout.Y_AXIS));
        formContainer.setOpaque(false);
        formContainer.setBorder(new EmptyBorder(80, 0, 0, 0));

        JLabel title = new JLabel("Join an Existing Group");
        title.setFont(new Font("Segoe UI", Font.BOLD, 36));
        title.setForeground(TEXT_MAIN);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel subtitle = new JLabel("Enter the 6-character code provided by the creator.");
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        subtitle.setForeground(TEXT_MUTED);
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        SearchField joinField = new SearchField("Enter code here...");
        joinField.setMaximumSize(new Dimension(350, 55));
        joinField.setAlignmentX(Component.CENTER_ALIGNMENT);

        ModernButton joinBtn = new ModernButton("Join Group", ACCENT, Color.WHITE, ACCENT);
        joinBtn.setMaximumSize(new Dimension(350, 50));
        joinBtn.setFont(new Font("Segoe UI", Font.BOLD, 16));
        joinBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        joinBtn.addActionListener(e -> {
            String code = joinField.getText().trim().toUpperCase();
            if (database.containsKey(code)) {
                currentGroup = database.get(code);
                refreshHome("");
                refreshGroupScreen();
                cardLayout.show(mainPanel, "GROUP_VIEW");
                joinField.resetPlaceholder();
            } else {
                JOptionPane.showMessageDialog(this, "Group not found! Please check the code.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        formContainer.add(title);
        formContainer.add(Box.createVerticalStrut(10));
        formContainer.add(subtitle);
        formContainer.add(Box.createVerticalStrut(50));
        formContainer.add(joinField);
        formContainer.add(Box.createVerticalStrut(15));
        formContainer.add(joinBtn);

        panel.add(topNav, BorderLayout.NORTH);
        panel.add(formContainer, BorderLayout.CENTER);

        return panel;
    }

    // ==========================================
    // SCREEN 3: GROUP VIEW (DASHBOARD)
    // ==========================================
    private JPanel createGroupScreen() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BG_APP);

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(BG_CARD);
        headerPanel.setBorder(new EmptyBorder(20, 30, 20, 30));

        JPanel leftHeader = new JPanel();
        leftHeader.setLayout(new BoxLayout(leftHeader, BoxLayout.Y_AXIS));
        leftHeader.setOpaque(false);

        JLabel backBtn = new JLabel("<html><b>&lt; Back</b></html>");
        backBtn.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        backBtn.setForeground(TEXT_MUTED);
        backBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        backBtn.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                currentGroup = null;
                refreshHome("");
                cardLayout.show(mainPanel, "HOME");
            }
        });

        groupHeaderLabel = new JLabel("Group name");
        groupHeaderLabel.setFont(new Font("Segoe UI", Font.BOLD, 30));
        groupHeaderLabel.setForeground(TEXT_MAIN);

        groupSubHeaderLabel = new JLabel("Code: XXXXXX  |  Category");
        groupSubHeaderLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        groupSubHeaderLabel.setForeground(TEXT_MUTED);

        leftHeader.add(backBtn);
        leftHeader.add(Box.createVerticalStrut(10));
        leftHeader.add(groupHeaderLabel);
        leftHeader.add(Box.createVerticalStrut(5));
        leftHeader.add(groupSubHeaderLabel);

        JPanel actionBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actionBtns.setOpaque(false);

        ModernButton deleteBtn = new ModernButton("Delete", new Color(250, 240, 240), DANGER, BORDER_COLOR);
        deleteBtn.setPreferredSize(new Dimension(80, 35));
        deleteBtn.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(this, "Are you sure you want to delete this group?\nIt will be moved to Recently Deleted.", "Confirm", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                deletedDatabase.put(currentGroup.code, currentGroup);
                database.remove(currentGroup.code);

                currentGroup = null;
                refreshHome("");
                cardLayout.show(mainPanel, "HOME");
            }
        });

        actionBtns.add(deleteBtn);

        headerPanel.add(leftHeader, BorderLayout.WEST);
        headerPanel.add(actionBtns, BorderLayout.EAST);

        JPanel membersSection = new JPanel(new BorderLayout());
        membersSection.setBackground(BG_APP);
        membersSection.setBorder(new EmptyBorder(15, 30, 5, 30));

        JPanel membersLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        membersLeft.setOpaque(false);
        membersCountLabel = new JLabel("Members (0):  ");
        membersCountLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        membersCountLabel.setForeground(TEXT_MAIN);
        membersLeft.add(membersCountLabel);

        membersFlowPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        membersFlowPanel.setOpaque(false);

        JScrollPane memScroll = new JScrollPane(membersFlowPanel);
        memScroll.setBorder(null);
        memScroll.setOpaque(false);
        memScroll.getViewport().setOpaque(false);
        memScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
        memScroll.setPreferredSize(new Dimension(550, 45));

        membersLeft.add(memScroll);

        ModernButton addMemberBtn = new ModernButton("+ Add Member", BG_CARD, TEXT_MAIN, BORDER_COLOR);
        addMemberBtn.setPreferredSize(new Dimension(130, 35));
        addMemberBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        addMemberBtn.addActionListener(e -> addMember());

        membersSection.add(membersLeft, BorderLayout.CENTER);
        membersSection.add(addMemberBtn, BorderLayout.EAST);

        expensesListPanel = new JPanel();
        expensesListPanel.setLayout(new BoxLayout(expensesListPanel, BoxLayout.Y_AXIS));
        expensesListPanel.setOpaque(false);

        JPanel listWrapper = new JPanel(new BorderLayout());
        listWrapper.setOpaque(false);
        listWrapper.setBorder(new EmptyBorder(10, 30, 10, 30));
        listWrapper.add(expensesListPanel, BorderLayout.NORTH);

        JScrollPane scrollPane = new JScrollPane(listWrapper);
        scrollPane.setBorder(null);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        JPanel bodyPanel = new JPanel(new BorderLayout());
        bodyPanel.setOpaque(false);
        bodyPanel.add(membersSection, BorderLayout.NORTH);
        bodyPanel.add(scrollPane, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new GridLayout(1, 2, 15, 0));
        bottomPanel.setOpaque(false);
        bottomPanel.setBorder(new EmptyBorder(15, 30, 25, 30));

        ModernButton addExpBtn = new ModernButton("Add Expense", BG_CARD, TEXT_MAIN, BORDER_COLOR);
        addExpBtn.setFont(new Font("Segoe UI", Font.BOLD, 16));

        ModernButton splitBtn = new ModernButton("Split Bill", ACCENT, Color.WHITE, ACCENT);
        splitBtn.setFont(new Font("Segoe UI", Font.BOLD, 16));

        addExpBtn.addActionListener(e -> addExpense());
        splitBtn.addActionListener(e -> calculateSplit());

        bottomPanel.add(addExpBtn);
        bottomPanel.add(splitBtn);

        panel.add(headerPanel, BorderLayout.NORTH);
        panel.add(bodyPanel, BorderLayout.CENTER);
        panel.add(bottomPanel, BorderLayout.SOUTH);

        return panel;
    }

    private void refreshGroupScreen() {
        if (currentGroup == null) return;

        groupHeaderLabel.setText(currentGroup.name);
        groupSubHeaderLabel.setText("Code: " + currentGroup.code + "  |  " + currentGroup.category);

        membersCountLabel.setText("Members (" + currentGroup.members.size() + "):  ");
        membersFlowPanel.removeAll();
        for (String member : currentGroup.members) {
            membersFlowPanel.add(createMemberTag(member));
        }
        membersFlowPanel.revalidate();
        membersFlowPanel.repaint();

        expensesListPanel.removeAll();

        if (currentGroup.expenses.isEmpty()) {
            JLabel noExpLabel = new JLabel("No expenses added yet. Click 'Add Expense' below to start.");
            noExpLabel.setFont(new Font("Segoe UI", Font.ITALIC, 14));
            noExpLabel.setForeground(TEXT_MUTED);
            expensesListPanel.add(noExpLabel);
        } else {
            for (Expense e : currentGroup.expenses) {
                JPanel expCard = new JPanel(new BorderLayout());
                expCard.setOpaque(false);
                expCard.setBorder(new EmptyBorder(0, 0, 12, 0));

                JPanel innerCard = new JPanel(new BorderLayout()) {
                    @Override
                    protected void paintComponent(Graphics g) {
                        Graphics2D g2 = (Graphics2D) g.create();
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        g2.setColor(BG_CARD);
                        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                        g2.setColor(BORDER_COLOR);
                        g2.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, 12, 12);
                        g2.dispose();
                        super.paintComponent(g);
                    }
                };
                innerCard.setOpaque(false);
                innerCard.setBorder(new EmptyBorder(20, 25, 20, 25));

                JLabel descLabel = new JLabel(e.description);
                descLabel.setForeground(TEXT_MAIN);
                descLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));

                JLabel amtLabel = new JLabel("₱" + String.format("%,.2f", e.amount));
                amtLabel.setForeground(TEXT_MAIN);
                amtLabel.setFont(new Font("Segoe UI", Font.BOLD, 20));

                innerCard.add(descLabel, BorderLayout.WEST);
                innerCard.add(amtLabel, BorderLayout.EAST);

                expCard.add(innerCard, BorderLayout.CENTER);
                expCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 85));
                expensesListPanel.add(expCard);
            }
        }

        expensesListPanel.revalidate();
        expensesListPanel.repaint();
    }

    private JPanel createMemberTag(String name) {
        JPanel tag = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(225, 230, 235));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        tag.setOpaque(false);
        tag.setBorder(new EmptyBorder(6, 12, 6, 12));

        JLabel nameLabel = new JLabel(name);
        nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        nameLabel.setForeground(TEXT_MAIN);
        tag.add(nameLabel);

        JLabel removeLabel = new JLabel("×");
        removeLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        removeLabel.setForeground(DANGER);
        removeLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
        removeLabel.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                currentGroup.members.remove(name);
                refreshGroupScreen();
            }
        });
        tag.add(removeLabel);

        return tag;
    }

    // ==========================================
    // LOGIC & FUNCTIONALITY
    // ==========================================
    private void addMember() {
        if (currentGroup.category.equalsIgnoreCase("Partner") && currentGroup.members.size() >= 2) {
            JOptionPane.showMessageDialog(this, "Partner groups can only have a maximum of 2 members.", "Limit Reached", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String name = JOptionPane.showInputDialog(this, "Enter New Member Name:");
        if (name == null) return;

        name = name.trim();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Member name cannot be empty or just spaces.", "Invalid Name", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (name.length() > 60) {
            JOptionPane.showMessageDialog(this, "Member name is too long! Maximum 60 characters allowed.", "Invalid Name", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (currentGroup.members.contains(name)) {
            JOptionPane.showMessageDialog(this, "Member already exists in this group.", "Duplicate", JOptionPane.WARNING_MESSAGE);
            return;
        }

        currentGroup.members.add(name);
        refreshGroupScreen();
    }

    private void addExpense() {
        if (currentGroup.members.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please add members first before adding an expense.");
            return;
        }

        String desc = JOptionPane.showInputDialog(this, "What is the expense for?");
        if (desc == null) return;

        desc = desc.trim();
        if (desc.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Expense description cannot be empty or just spaces.", "Invalid Description", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            String amountStr = JOptionPane.showInputDialog(this, "Total Amount (₱):");
            if (amountStr == null) return;
            double amount = Double.parseDouble(amountStr);
            if (amount <= 0) throw new NumberFormatException();

            currentGroup.expenses.add(new Expense(desc, amount));
            refreshGroupScreen();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Invalid amount entered. Please enter a valid number.", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void calculateSplit() {
        if (currentGroup == null || currentGroup.members.isEmpty() || currentGroup.expenses.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Ensure you have both members and expenses added.");
            return;
        }
        int membersCount = currentGroup.members.size();
        double total = currentGroup.getTotalExpenses();
        double share = total / membersCount;

        StringBuilder sb = new StringBuilder();
        sb.append("=== SPLIT BILL SUMMARY ===\n\n");
        sb.append("Total Expense : ₱").append(String.format("%,.2f", total)).append("\n");
        sb.append("Total Members : ").append(membersCount).append("\n");
        sb.append("--------------------------------------\n");
        sb.append("COST PER PERSON : ₱").append(String.format("%,.2f", share)).append("\n");
        sb.append("--------------------------------------\n\n");

        sb.append("Everyone needs to contribute:\n\n");
        for (String member : currentGroup.members) {
            sb.append(" • ").append(member).append(" owes ₱").append(String.format("%,.2f", share)).append("\n");
        }

        showCustomDialog("Split Calculation", sb.toString());
    }

    private void showCustomDialog(String title, String content) {
        JTextArea area = new JTextArea(content);
        area.setEditable(false);
        area.setFont(new Font("Monospaced", Font.PLAIN, 14));
        area.setBackground(BG_CARD);
        area.setBorder(new EmptyBorder(15, 15, 15, 15));

        JScrollPane scroll = new JScrollPane(area);
        scroll.setPreferredSize(new Dimension(380, 320));
        scroll.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));

        JOptionPane.showMessageDialog(this, scroll, title, JOptionPane.PLAIN_MESSAGE);
    }

    private String generateUniqueCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        SecureRandom rand = new SecureRandom();
        String code;
        do {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 6; i++) sb.append(chars.charAt(rand.nextInt(chars.length())));
            code = sb.toString();
        } while (database.containsKey(code) || deletedDatabase.containsKey(code));
        return code;
    }

    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch(Exception ignored){}
        SwingUtilities.invokeLater(() -> new SmartKKB().setVisible(true));
    }

    // ==========================================
    // CUSTOM UI GRAPHICS CLASSES
    // ==========================================

    class SearchField extends JTextField {
        private String placeholder;

        public SearchField(String placeholder) {
            this.placeholder = placeholder;
            setText(placeholder);
            setFont(new Font("Segoe UI", Font.PLAIN, 15));
            setForeground(TEXT_MUTED);
            setHorizontalAlignment(JTextField.LEFT);
            setOpaque(false);
            setBorder(new EmptyBorder(10, 15, 10, 15));

            addFocusListener(new FocusAdapter() {
                @Override
                public void focusGained(FocusEvent e) {
                    if (getText().equals(placeholder)) {
                        setText("");
                        setForeground(TEXT_MAIN);
                    }
                }
                @Override
                public void focusLost(FocusEvent e) {
                    if (getText().isEmpty()) {
                        resetPlaceholder();
                    }
                }
            });
        }

        public void resetPlaceholder() {
            setText(placeholder);
            setForeground(TEXT_MUTED);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(BG_CARD);
            g2.fill(new RoundRectangle2D.Double(0, 0, getWidth()-1, getHeight()-1, 10, 10));
            g2.setColor(BORDER_COLOR);
            g2.draw(new RoundRectangle2D.Double(0, 0, getWidth()-1, getHeight()-1, 10, 10));
            g2.dispose();
            super.paintComponent(g);
        }
    }

    class ModernButton extends JButton {
        private Color bgColor, fgColor, hoverColor, borderColor;

        public ModernButton(String text, Color bgColor, Color fgColor, Color borderColor) {
            super(text);
            this.bgColor = bgColor;
            this.fgColor = fgColor;
            this.borderColor = borderColor;

            this.hoverColor = new Color(
                    Math.max(bgColor.getRed() - 15, 0),
                    Math.max(bgColor.getGreen() - 15, 0),
                    Math.max(bgColor.getBlue() - 15, 0)
            );

            setFont(new Font("Segoe UI", Font.PLAIN, 14));
            setForeground(fgColor);
            setContentAreaFilled(false);
            setFocusPainted(false);
            setBorderPainted(false);
            setCursor(new Cursor(Cursor.HAND_CURSOR));

            addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) { setBackground(hoverColor); }
                public void mouseExited(MouseEvent e) { setBackground(bgColor); }
            });
            setBackground(bgColor);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(getBackground());
            g2.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 10, 10));
            g2.setColor(borderColor);
            g2.draw(new RoundRectangle2D.Double(0, 0, getWidth()-1, getHeight()-1, 10, 10));
            g2.dispose();
            super.paintComponent(g);
        }
    }

    class GroupCard extends JPanel {
        public GroupCard(ExpenseGroup group) {
            setLayout(new BorderLayout());
            setOpaque(false);
            setPreferredSize(new Dimension(650, 100));
            setMaximumSize(new Dimension(800, 100));
            setBorder(new EmptyBorder(15, 25, 15, 25));

            String text = String.format("<html><span style='font-size:20px; font-weight:bold; color:#282C32;'>%s</span><br>"
                            + "<span style='font-size:14px; color:#82878E;'>%d members &nbsp;•&nbsp; %s &nbsp;•&nbsp; <b>Code: %s</b></span></html>",
                    group.name, group.members.size(), group.category, group.code);

            JLabel label = new JLabel(text);
            add(label, BorderLayout.WEST);
        }
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(BG_CARD);
            g2.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 12, 12));
            g2.setColor(BORDER_COLOR);
            g2.draw(new RoundRectangle2D.Double(0, 0, getWidth()-1, getHeight()-1, 12, 12));
            g2.dispose();
            super.paintComponent(g);
        }
    }

    // ==========================================
    // DATA MODELS
    // ==========================================

    // ADDED: implements Serializable so Java can save these objects to a file
    class ExpenseGroup implements Serializable {
        private static final long serialVersionUID = 1L; // Recommended for Serializable classes

        String code;
        String category;
        String name;
        List<String> members = new ArrayList<>();
        List<Expense> expenses = new ArrayList<>();

        ExpenseGroup(String code, String category, String name) {
            this.code = code;
            this.category = category;
            this.name = name;
        }
        double getTotalExpenses() {
            double total = 0;
            for (Expense e : expenses) total += e.amount;
            return total;
        }
    }

    // ADDED: implements Serializable
    class Expense implements Serializable {
        private static final long serialVersionUID = 1L;

        String description;
        double amount;
        Expense(String description, double amount) {
            this.description = description;
            this.amount = amount;
        }
    }
}