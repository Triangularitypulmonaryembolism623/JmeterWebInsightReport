package com.jmeterwebinsightreport.report.listener;

import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.visualizers.gui.AbstractListenerGui;

import javax.swing.*;
import java.awt.*;

/**
 * GUI configuration panel for the Web Insight Report Listener.
 */
public class WebInsightReportListenerGui extends AbstractListenerGui {

    private static final long serialVersionUID = 1L;

    private JTextField outputDirectoryField;
    private JTextField reportTitleField;
    private JTextField reportFilenameField;
    private JCheckBox generateJunitXmlCheckbox;

    public WebInsightReportListenerGui() {
        init();
    }

    private void init() {
        setLayout(new BorderLayout(0, 5));
        setBorder(makeBorder());

        add(makeTitlePanel(), BorderLayout.NORTH);

        JPanel configPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(2, 2, 2, 2);

        // Report title
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        configPanel.add(new JLabel("Report Title:"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.gridwidth = 2;
        reportTitleField = new JTextField("JMeter Web Insight Report", 30);
        configPanel.add(reportTitleField, gbc);

        // Report filename
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        gbc.gridwidth = 1;
        configPanel.add(new JLabel("Report Filename:"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.gridwidth = 2;
        reportFilenameField = new JTextField(30);
        reportFilenameField.setToolTipText("Default: web-insight-report.html. Supports ${timestamp} placeholder.");
        configPanel.add(reportFilenameField, gbc);

        // Output directory with browse button
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0;
        gbc.gridwidth = 1;
        configPanel.add(new JLabel("Output Directory:"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        outputDirectoryField = new JTextField(30);
        configPanel.add(outputDirectoryField, gbc);
        gbc.gridx = 2;
        gbc.weightx = 0;
        JButton browseButton = new JButton("Browse...");
        browseButton.addActionListener(e -> browseOutputDirectory());
        configPanel.add(browseButton, gbc);

        // Generate JUnit XML checkbox
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.weightx = 0;
        gbc.gridwidth = 1;
        configPanel.add(new JLabel(""), gbc); // empty label for alignment
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.gridwidth = 2;
        generateJunitXmlCheckbox = new JCheckBox("Generate JUnit XML", false);
        generateJunitXmlCheckbox.setToolTipText("Generate a JUnit XML report alongside the HTML report (for CI/CD integration).");
        configPanel.add(generateJunitXmlCheckbox, gbc);

        add(configPanel, BorderLayout.CENTER);
    }

    private void browseOutputDirectory() {
        JFileChooser chooser = new JFileChooser(outputDirectoryField.getText());
        chooser.setDialogTitle("Select Output Directory");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            outputDirectoryField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    @Override
    public String getLabelResource() {
        return "web_insight_report_listener";
    }

    @Override
    public String getStaticLabel() {
        return "Web Insight Report";
    }

    @Override
    public TestElement createTestElement() {
        WebInsightReportListener listener = new WebInsightReportListener();
        modifyTestElement(listener);
        return listener;
    }

    @Override
    public void modifyTestElement(TestElement element) {
        super.configureTestElement(element);
        element.setProperty(WebInsightReportListener.REPORT_TITLE, reportTitleField.getText());
        element.setProperty(WebInsightReportListener.REPORT_FILENAME, reportFilenameField.getText());
        element.setProperty(WebInsightReportListener.REPORT_OUTPUT_DIR, outputDirectoryField.getText());
        element.setProperty(WebInsightReportListener.GENERATE_JUNIT_XML, generateJunitXmlCheckbox.isSelected());
    }

    @Override
    public void configure(TestElement element) {
        super.configure(element);
        reportTitleField.setText(element.getPropertyAsString(
                WebInsightReportListener.REPORT_TITLE, "JMeter Web Insight Report"));
        reportFilenameField.setText(element.getPropertyAsString(
                WebInsightReportListener.REPORT_FILENAME, ""));
        outputDirectoryField.setText(element.getPropertyAsString(
                WebInsightReportListener.REPORT_OUTPUT_DIR, ""));
        generateJunitXmlCheckbox.setSelected(element.getPropertyAsBoolean(
                WebInsightReportListener.GENERATE_JUNIT_XML, false));
    }

    @Override
    public void clearGui() {
        super.clearGui();
        reportTitleField.setText("JMeter Web Insight Report");
        reportFilenameField.setText("");
        outputDirectoryField.setText("");
        generateJunitXmlCheckbox.setSelected(false);
    }
}
