package de.lego.gottfried.decdat;

import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumnModel;

import de.lego.gottfried.decdat.dat.Dat;
import de.lego.gottfried.decdat.dat.DatSymbol;
import de.lego.gottfried.decdat.decompiler.Decompiler;
import de.lego.gottfried.decdat.exporter.Exporter;
import de.lego.gottfried.decdat.gui.D2FileChooser;
import de.lego.gottfried.decdat.gui.D2Help;
import de.lego.gottfried.decdat.gui.D2TableModel;
import de.lego.gottfried.decdat.util.DaedalusFileFilter;
import de.lego.gottfried.decdat.util.DatFileFilter;

public class MainForm implements CaretListener, ActionListener, ListSelectionListener {
	private static final String		VersionString	= "1.0b";
	private static final String		logFile			= "d2.log";

	protected static MainForm		inst;
	private List<DatSymbol>			currCol			= new LinkedList<DatSymbol>();
	private File					file;

	public static Dat				theDat;

	private static int				indent			= 2;
	private static final String		idt				= "               ";
	private static StringBuilder	sb				= new StringBuilder();
	private static SimpleDateFormat	dfm				= new SimpleDateFormat("HH:mm:ss");

	private static void log(String i, String t) {
		sb.setLength(0);
		System.out.println(sb.append('[').append(dfm.format(new Date())).append("] ").append(i).append(":").append(idt.substring(0, indent)).append(t).toString());
	}

	public static void Indent(int i) {
		indent += i;
		if(indent < 2)
			indent = 2;
		else if(indent > 14)
			indent = 14;
	}

	public static void Log(String t) {
		log("Inf", t);
	}

	public static void LogErr(String t) {
		log("ERR", t);
	}

	public static void Err(String t) {
		JOptionPane.showMessageDialog(frmDecdat, t, "Error", 0);
	}

	public static void Inf(String t) {
		JOptionPane.showMessageDialog(frmDecdat, t, "Information", 1);
	}

	public static void main(String[] args) {
		PrintStream ps;
		try {
			ps = new PrintStream(new BufferedOutputStream(new FileOutputStream(new File(logFile))), true);
			System.setOut(ps);
			System.setErr(ps);
		} catch(FileNotFoundException e1) {
			Err("Log file could not be initialised.");
		}

		Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
			public void uncaughtException(Thread t, Throwable e) {
				LogErr("unhandled exception occured: " + e.toString());
				e.printStackTrace();

				JOptionPane.showMessageDialog(null, "An unhandled exception has occurred:\n  " + e.toString() + "\n\nDer zugeh�rige Stacktrace ist im Logfile zu finden.", "Unbehandelte Ausnahme", 0);
			}
		});

		EventQueue.invokeLater(new Runnable() {
			public void run() {
				new MainForm();
				frmDecdat.setVisible(true);
			}
		});
	}

	public MainForm() {
		inst = this;
		initialize();
	}

	private File getSelectedFile() {
		return file = D2FileChooser.get(DatFileFilter.inst);
	}

	private File getSelectedDFile() {
		return file = D2FileChooser.get(DaedalusFileFilter.inst);
	}

	private File getSelectedDirectory() {
		return file = D2FileChooser.get(null);
	}

	private void selectDat() {
		if(getSelectedFile() == null)
			return;

		theDat = new Dat(file.getAbsolutePath());

		if(!theDat.fileOk) {
			Err("The specified DAT file could not be loaded.");
			showSymbols(null);
			return;
		}

		showSymbols(theDat.SymbolsC);
	}

	private void searchSymbols() {
		if(theDat == null)
			return;
		List<DatSymbol> col;
		if(txtKeyword.getText().length() == 0)
			col = theDat.SymbolsC;
		else
			switch(cbxSearch.getSelectedIndex()) {
				case 0:
					col = theDat.getAllSymbolIDs(txtKeyword.getText());
					break;
				case 1:
					col = theDat.getAllSymbolsByName(txtKeyword.getText());
					break;
				case 2:
					col = theDat.getAllSymbolsByType(txtKeyword.getText());
					break;
				default:
					col = null;
			}
		showSymbols(col);
	}

	private void showSymbols(List<DatSymbol> sym) {
		if(currCol.equals(sym)) {
			Log("ignore request.");
			return;
		}
		currCol = sym;
		tblModel.setRowCount(0);
		for(DatSymbol s : sym)
			tblModel.addRow(new Object[] { s.id, s.name, s.getTypeString() });
	}

	public static JFrame		frmDecdat;
	private JTextField			txtKeyword;
	private JTable				tblResults;
	private DefaultTableModel	tblModel;
	private JComboBox<String>	cbxSearch;
	private JButton				btnSearch;
	private JTable				tblSymbol;
	private JTextArea			txtrEditor;
	private JTextArea			txtrEditorop;
	private JTextArea			txtrEditorexp;
	private JTabbedPane			tabbedPane;

	@Override
	public void caretUpdate(CaretEvent e) {
		if(txtKeyword.getText().length() == 0)
			searchSymbols();
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		if(e.getSource() == btnSearch)
			searchSymbols();
		else if(e.getSource() instanceof JMenuItem) {
			JMenuItem itm = (JMenuItem)e.getSource();
			switch(itm.getText()) {
				case "Versionsinfo":
					JOptionPane.showMessageDialog(frmDecdat, "DecDat\nVersion " + VersionString + "\n\nvon Gottfried - 2012\n& Auronen - 2022", "Versionsinfo", 1);
					return;

				case "Tokens":
					D2Help.Open("Tokens");
					return;
				case "RegEx":
					D2Help.Open("RegEx");
					return;
				case "Exportdefinition":
					D2Help.Open("ExportDef");
					return;

				case "Show log":
					try {
						Desktop.getDesktop().open(new File(logFile));
					} catch(IOException e1) {
						Err("Could not open a log file.");
					}
					return;

				case "Open...":
					selectDat();
					return;
				case "Quit":
					System.exit(0);
					return;

				case "Into one file...":
					if(theDat == null) {
						Err("No DAT file loaded yet.!");
						return;
					}
					if(getSelectedDFile() != null)
						if(Exporter.ToFile(theDat.SymbolsRegular, file))
							Inf("All symbols were successfully exported!");
					return;
				case "With export definitions...":
					if(theDat == null) {
						Err("No DAT file loaded yet.!");
						return;
					}
					if(getSelectedDirectory() != null)
						if(Exporter.ToFile(txtrEditorexp.getText(), file))
							Inf("The export definition has been completely processed.!");
					return;
				default:
					if(tblResults.getSelectedRow() == -1) {
						JOptionPane.showMessageDialog(frmDecdat, "First, you have to select a symbol!", "Warning", 2);
						return;
					}
					if(getSelectedDFile() != null)
						if(Exporter.ToFile(theDat.Symbols[(int)tblModel.getValueAt(tblResults.getSelectedRow(), 0)], file))
							Inf("The symbol was successfully exported!");
			}

		}
	}

	private int	lastRow	= -1;

	@Override
	public void valueChanged(ListSelectionEvent arg0) {
		int row = tblResults.getSelectedRow();
		if(row == lastRow || row == -1)
			return;
		lastRow = row;

		DatSymbol d = theDat.Symbols[(int)tblModel.getValueAt(row, 0)];
		tblSymbol.setValueAt("" + d.id, 0, 1);
		tblSymbol.setValueAt(d.name, 1, 1);
		tblSymbol.setValueAt(d.ele(), 2, 1);
		tblSymbol.setValueAt(d.typeToString(), 3, 1);
		tblSymbol.setValueAt(d.flagsToString(), 4, 1);
		tblSymbol.setValueAt(d.offset, 5, 1);
		tblSymbol.setValueAt(d.parent, 6, 1);
		tblSymbol.setValueAt(d.contentToString(), 7, 1);
		tblSymbol.setValueAt(d.filenr, 8, 1);
		tblSymbol.setValueAt(d.line, 9, 1);
		tblSymbol.setValueAt(d.line_anz, 10, 1);
		tblSymbol.setValueAt(d.pos_beg, 11, 1);
		tblSymbol.setValueAt(d.pos_anz, 12, 1);
		txtrEditor.setText(d.toString());
		Decompiler p = Decompiler.getOP(d);
		if(p == null)
			txtrEditorop.setText("");
		else
			txtrEditorop.setText(p.toString());
	}

	private void initialize() {
		frmDecdat = new JFrame();
		frmDecdat.setTitle("DecDat - Auronen Gothic Demo edition");
		Dimension d = Toolkit.getDefaultToolkit().getScreenSize();
		int size_x = 1280;
		int size_y = 800;
		frmDecdat.setBounds(d.width / 2 - (size_x/2), d.height / 2 - (size_y/2), size_x, size_y);
		frmDecdat.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frmDecdat.getContentPane().setLayout(new BorderLayout(0, 0));

		JSplitPane splitPane = new JSplitPane();
		splitPane.setResizeWeight(0.3);
		splitPane.setContinuousLayout(true);
		frmDecdat.getContentPane().add(splitPane);

		JPanel panel0 = new JPanel();
		splitPane.setLeftComponent(panel0);
		panel0.setLayout(new BorderLayout(0, 0));

		JPanel panel1 = new JPanel();
		panel0.add(panel1, BorderLayout.SOUTH);
		panel1.setLayout(new BorderLayout(0, 0));

		txtKeyword = new JTextField();
		panel1.add(txtKeyword, BorderLayout.CENTER);
		txtKeyword.setColumns(10);
		txtKeyword.addCaretListener(this);

		cbxSearch = new JComboBox<String>();
		cbxSearch.setModel(new DefaultComboBoxModel<String>(new String[] { "ID", "Name", "Type" }));
		panel1.add(cbxSearch, BorderLayout.WEST);

		btnSearch = new JButton("Search");
		btnSearch.addActionListener(this);
		panel1.add(btnSearch, BorderLayout.EAST);

		tblResults = new JTable();
		tblResults.setShowHorizontalLines(false);
		tblResults.getSelectionModel().addListSelectionListener(this);
		tblResults.setFillsViewportHeight(true);
		tblResults.setBorder(null);
		tblResults.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		tblResults.setModel(tblModel = new D2TableModel(new String[] { "ID", "Symbolname", "Type" }));
		tblResults.getColumnModel().getColumn(0).setPreferredWidth(50);
		tblResults.getColumnModel().getColumn(0).setMinWidth(50);
		tblResults.getColumnModel().getColumn(0).setMaxWidth(75);
		tblResults.getColumnModel().getColumn(2).setPreferredWidth(60);

		JScrollPane scroll = new JScrollPane(tblResults);
		panel0.add(scroll, BorderLayout.CENTER);

		tblResults.getTableHeader().addMouseListener(new MouseAdapter() {
			public void mouseClicked(MouseEvent e) {
				TableColumnModel colModel = tblResults.getColumnModel();
				int columnModelIndex = colModel.getColumnIndexAtX(e.getX());
				int modelIndex = colModel.getColumn(columnModelIndex).getModelIndex();

				if(modelIndex < 0)
					return;

				switch(modelIndex) {
					case 0:
						Dat.sortSymbolsByID(currCol);
						break;
					case 1:
						Dat.sortSymbolsByName(currCol);
						break;
					default:
						Dat.sortSymbolsByType(currCol);
				}
				showSymbols(currCol);
			}
		});

		panel0.add(tblResults.getTableHeader(), BorderLayout.NORTH);

		JPanel panel2 = new JPanel();
		splitPane.setRightComponent(panel2);
		panel2.setLayout(new BorderLayout(0, 0));

		tblSymbol = new JTable();
		tblSymbol.setRowSelectionAllowed(false);
		tblSymbol.setModel(new D2TableModel(
				new Object[][] {
						{ "id", null },
						{ "name", null },
						{ "ele", null },
						{ "type", null },
						{ "flags", null },
						{ "offset", null },
						{ "parent", null },
						{ "content", null },
						{ "filenr", null },
						{ "line", null },
						{ "line_anz", null },
						{ "pos_beg", null },
						{ "pos_anz", null },
				},
				new String[] {
						"Data", "Value"
				}));
		tblSymbol.getColumnModel().getColumn(0).setPreferredWidth(50);
		tblSymbol.getColumnModel().getColumn(0).setMinWidth(50);
		tblSymbol.getColumnModel().getColumn(0).setMaxWidth(50);
		panel2.add(tblSymbol, BorderLayout.SOUTH);

		tabbedPane = new JTabbedPane(JTabbedPane.TOP);
		panel2.add(tabbedPane, BorderLayout.CENTER);

		JScrollPane scrollPane0 = new JScrollPane();
		tabbedPane.addTab("Source code", null, scrollPane0, null);

		txtrEditor = new JTextArea();
		txtrEditor.setTabSize(4);
		txtrEditor.setFont(new Font("Courier New", Font.PLAIN, 13));
		scrollPane0.setViewportView(txtrEditor);

		JScrollPane scrollPane1 = new JScrollPane();
		tabbedPane.addTab("Tokens", null, scrollPane1, null);

		txtrEditorop = new JTextArea();
		txtrEditorop.setFont(new Font("Courier New", Font.PLAIN, 13));
		txtrEditorop.setTabSize(4);
		scrollPane1.setViewportView(txtrEditorop);

		JScrollPane scrollPane2 = new JScrollPane();
		tabbedPane.addTab("Export definitions", null, scrollPane2, null);

		txtrEditorexp = new JTextArea();
		txtrEditorexp.setText("938\t\t_intern/Externals.d\n1473\t_intern/Constants.d\n1620\t_intern/Classes.d\n\n;4922\tIkarus/Engineclasses.d\n;5129\tIkarus/Ikarus_Constants_G2.d\n;6335\tIkarus/Ikarus.d\n\n-\t\tContent.d");
		txtrEditorexp.setTabSize(4);
		txtrEditorexp.setFont(new Font("Courier New", Font.PLAIN, 13));
		scrollPane2.setViewportView(txtrEditorexp);

		JMenuBar menuBar = new JMenuBar();
		frmDecdat.setJMenuBar(menuBar);

		JMenu mnDatei = new JMenu("File");
		menuBar.add(mnDatei);

		JMenuItem mntmOpen = new JMenuItem("Open...");
		mntmOpen.addActionListener(this);
		mnDatei.add(mntmOpen);

		mnDatei.add(new JSeparator());

		JMenuItem mntmExit = new JMenuItem("Quit");
		mntmExit.addActionListener(this);
		mnDatei.add(mntmExit);

		JMenu mnExportieren = new JMenu("Export");
		menuBar.add(mnExportieren);

		JMenuItem mntmAlleSymbole = new JMenuItem("Into one file...");
		mntmAlleSymbole.addActionListener(this);
		mnExportieren.add(mntmAlleSymbole);

		JMenuItem mntmNachSymbolid = new JMenuItem("With export definitions...");
		mntmNachSymbolid.addActionListener(this);
		mnExportieren.add(mntmNachSymbolid);

		mnExportieren.add(new JSeparator());

		JMenuItem mntmAktuellesSymbol = new JMenuItem("Only selected Symbol...");
		mntmAktuellesSymbol.addActionListener(this);
		mnExportieren.add(mntmAktuellesSymbol);

		JMenu mnHilfe = new JMenu("Hilfe");
		menuBar.add(mnHilfe);

		JMenuItem mntmVersionsinfo = new JMenuItem("Versionsinfo");
		mntmVersionsinfo.addActionListener(this);
		mnHilfe.add(mntmVersionsinfo);

		JMenuItem mntmShowLog = new JMenuItem("Show log");
		mntmShowLog.addActionListener(this);
		mnHilfe.add(mntmShowLog);

		mnHilfe.add(new JSeparator());

		JMenuItem mntmRegEx = new JMenuItem("RegEx");
		mntmRegEx.addActionListener(this);
		mnHilfe.add(mntmRegEx);

		JMenuItem mntmTokens = new JMenuItem("Tokens");
		mntmTokens.addActionListener(this);
		mnHilfe.add(mntmTokens);

		JMenuItem mntmExportdef = new JMenuItem("Exportdefinition");
		mntmExportdef.addActionListener(this);
		mnHilfe.add(mntmExportdef);
	}
}