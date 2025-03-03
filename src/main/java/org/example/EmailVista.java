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
    private Gmail servicio;
    private List<Message> mensaje;
    private JTable tablaemail;
    private DefaultTableModel tablaModelo;
    private JButton boton;

    public EmailVista(Gmail servicio, List<Message> mensajes) {
        this.servicio = servicio;
        this.mensaje = mensajes;

        setTitle("Gestor de Correos - Gmail Labeler");
        setSize(600, 400);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        JPanel panelPrincipal = new JPanel(new BorderLayout(10, 10));
        panelPrincipal.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        String[] nombreColumnas = {"Asunto", "Etiquetas"};
        tablaModelo = new DefaultTableModel(nombreColumnas, 0);
        tablaemail = new JTable(tablaModelo);
        JScrollPane panelDeslizable = new JScrollPane(tablaemail);

        boton = new JButton("Etiquetar correos");
        boton.setFont(new Font("Arial", Font.BOLD, 14));
        boton.setBackground(new Color(30, 144, 255));
        boton.setForeground(Color.WHITE);
        boton.setFocusPainted(false);
        boton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                etiquetarCorreos();
            }
        });

        panelPrincipal.add(panelDeslizable, BorderLayout.CENTER);
        panelPrincipal.add(boton, BorderLayout.SOUTH);

        add(panelPrincipal);
        setVisible(true);
        cargarEmails(mensajes);
    }
    private void cargarEmails(List<Message> updatedMessages) {
        tablaModelo.setRowCount(0);

        try {

            Map<String, String> idToNameMap = EtiquetadorDeGmails.obtenerMapaEtiquetas(servicio);

            for (Message mensaje : updatedMessages) {
                String snippet = mensaje.getSnippet();
                List<String> etiquetaId = mensaje.getLabelIds();

                String etiquetas = etiquetaId != null
                        ? etiquetaId.stream()
                        .map(id -> idToNameMap.getOrDefault(id, id))
                        .filter(name -> !name.equals("IMPORTANT") && !name.equals("SENT") && !name.equals("INBOX"))
                        .reduce((a, b) -> a + ", " + b)
                        .orElse("Sin etiquetas")
                        : "Sin etiquetas";

                tablaModelo.addRow(new Object[]{snippet, etiquetas});
            }

            System.out.println("Tabla de correos actualizada correctamente.");
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error cargando correos", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void etiquetarCorreos() {
        try {
            mensaje = EtiquetadorDeGmails.etiquetarCorreos(servicio, mensaje);
            JOptionPane.showMessageDialog(this, "Correos etiquetados correctamente.", "Éxito", JOptionPane.INFORMATION_MESSAGE);
            cargarEmails(mensaje);
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error al etiquetar los correos", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

}
