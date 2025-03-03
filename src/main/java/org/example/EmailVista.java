package org.example;

import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Message;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class EmailVista extends JFrame {
    private Gmail service;
    private List<Message> messages;
    private JTable emailTable;
    private DefaultTableModel tableModel;
    private JButton labelButton;

    public EmailVista(Gmail service, List<Message> messages) {
        this.service = service;
        this.messages = messages;

        setTitle("Gestor de Correos - Gmail Labeler");
        setSize(600, 400);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        String[] columnNames = {"Asunto", "Etiquetas"};
        tableModel = new DefaultTableModel(columnNames, 0);
        emailTable = new JTable(tableModel);
        JScrollPane scrollPane = new JScrollPane(emailTable);

        labelButton = new JButton("Etiquetar correos");
        labelButton.setFont(new Font("Arial", Font.BOLD, 14));
        labelButton.setBackground(new Color(30, 144, 255));
        labelButton.setForeground(Color.WHITE);
        labelButton.setFocusPainted(false);
        labelButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                etiquetarCorreos();
            }
        });

        mainPanel.add(scrollPane, BorderLayout.CENTER);
        mainPanel.add(labelButton, BorderLayout.SOUTH);

        add(mainPanel);
        setVisible(true);
        cargarEmails(messages);
    }
    private void cargarEmails(List<Message> updatedMessages) {
        tableModel.setRowCount(0);

        try {

            Map<String, String> idToNameMap = EtiquetadorDeGmails.obtenerMapaEtiquetas(service);

            for (Message message : updatedMessages) {
                String snippet = message.getSnippet();
                List<String> labelIds = message.getLabelIds();

                String etiquetas = labelIds != null
                        ? labelIds.stream()
                        .map(id -> idToNameMap.getOrDefault(id, id))
                        .filter(name -> !name.equals("IMPORTANT") && !name.equals("SENT") && !name.equals("INBOX"))
                        .reduce((a, b) -> a + ", " + b)
                        .orElse("Sin etiquetas")
                        : "Sin etiquetas";

                tableModel.addRow(new Object[]{snippet, etiquetas});
            }

            System.out.println("Tabla de correos actualizada correctamente.");
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error cargando correos", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }




    private void etiquetarCorreos() {
        try {
            messages = EtiquetadorDeGmails.etiquetarCorreos(service, messages);
            JOptionPane.showMessageDialog(this, "Correos etiquetados correctamente.", "Éxito", JOptionPane.INFORMATION_MESSAGE);
            cargarEmails(messages);
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error al etiquetar los correos", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

}
