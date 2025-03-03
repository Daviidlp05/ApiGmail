package org.example;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.*;

import javax.swing.*;
import java.io.*;
import java.security.GeneralSecurityException;
import java.util.*;

public class EtiquetadorDeGmails {
    private static final String NOMBRE_APLICACION = "Gmail Labeler App";
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
    private static final String DIRECTORIO_TOKENS = "tokens";
    private static final List<String> SCOPES = Collections.singletonList("https://www.googleapis.com/auth/gmail.modify");
    private static final String CREDENCIALES = "src/main/resources/credentials.json";

    private static Gmail servicio;

    public static void main(String[] args) throws IOException, GeneralSecurityException {

        final NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        servicio = new Gmail.Builder(httpTransport, JSON_FACTORY, conseguiCredenciales(httpTransport))
                .setApplicationName(NOMBRE_APLICACION)
                .build();

        List<Message> mensajes = conseguirMensajes(servicio, "me");
        if (mensajes.isEmpty()) {
            System.out.println("No hay correos en la bandeja de entrada.");
            return;
        }

        SwingUtilities.invokeLater(() -> new EmailVista(servicio, mensajes));
    }
    private static Credential conseguiCredenciales(final NetHttpTransport httpTransport) throws IOException {
        File credencialRuta = new File(CREDENCIALES);
        if (!credencialRuta.exists()) {
            throw new FileNotFoundException("Archivo de credenciales no encontrado: " + credencialRuta.getAbsolutePath());
        }

        InputStream in = new FileInputStream(credencialRuta);
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                httpTransport, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(new File(DIRECTORIO_TOKENS)))
                .setAccessType("offline")
                .build();
        return new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("user");
    }

    static List<Message> conseguirMensajes(Gmail servicio, String userId) throws IOException {
        ListMessagesResponse response = servicio.users().messages().list(userId)
                .setQ("in:all")
                .setMaxResults(7L)
                .execute();

        List<Message> mensajes = response.getMessages();

        if (mensajes == null || mensajes.isEmpty()) {
            return new ArrayList<>();
        }

        List<Message> mensajesValidos = new ArrayList<>();
        for (Message mensaje : mensajes) {
            Message mensajeEntero = servicio.users().messages().get(userId, mensaje.getId()).execute();
            if (!mensajeEntero.getSnippet().contains("Address not found") && !mensajeEntero.getSnippet().contains("550 5.1.1")) {
                mensajesValidos.add(mensajeEntero);
            }
        }

        return mensajesValidos;
    }

    public static List<Message> etiquetarCorreos(Gmail servicio, List<Message> mensajes) throws IOException {
        if (mensajes.size() < 7) {
            System.out.println("No hay suficientes mensajes para etiquetar.");
            return mensajes;
        }

        Map<String, String> etiquetaDic = obtenerMapaEtiquetas(servicio);
        System.out.println("Iniciando etiquetado de correos...");

        int DoneCantidad = 3;
        int progreso = 1;
        for (int i = 0; i < mensajes.size(); i++) {
            Message mensaje = servicio.users().messages().get("me", mensajes.get(i).getId()).execute();
            List<String> EtiquetaID = mensaje.getLabelIds();

            if (EtiquetaID.contains(etiquetaDic.get("Done")) || EtiquetaID.contains(etiquetaDic.get("Work.in.Progress")) || EtiquetaID.contains(etiquetaDic.get("To.be.Done"))) {
                System.out.println("El mensaje " + mensaje.getId() + " ya tiene una etiqueta. Saltando...");
                continue;
            }

            String label = (i < DoneCantidad) ? "Done" : (i < DoneCantidad + progreso) ? "Work.in.Progress" : "To.be.Done";
            System.out.println("Asignando etiqueta '" + label + "' a mensaje ID: " + mensaje.getId());

            modificarMensaje(servicio, "me", mensaje.getId(), label);
        }

        System.out.println("Proceso de etiquetado finalizado.");

        return conseguirMensajes(servicio, "me");
    }


    private static void modificarMensaje(Gmail servicio, String usuarioId, String mensajeId, String mensajeEtiqueta) throws IOException {
        Map<String, String> etiquetaDic = obtenerMapaEtiquetas(servicio);
        String nuevaEtiquetaId = etiquetaDic.get(mensajeEtiqueta);

        if (nuevaEtiquetaId == null) {
            System.out.println("La etiqueta '" + mensajeEtiqueta + "' no se encontró en Gmail. Verifica que existe.");
            return;
        }

        Message mensaje = servicio.users().messages().get(usuarioId, mensajeId).execute();
        List<String> etiquetasActuales = mensaje.getLabelIds();

        if (etiquetasActuales.contains(nuevaEtiquetaId)) {
            System.out.println("El mensaje ya tiene la etiqueta '" + mensajeEtiqueta + "'. Saltando...");
            return;
        }

        List<String> etiquetasEliminar = new ArrayList<>();
        for (String nombreEtiquetas : Arrays.asList("Done", "Work.in.Progress", "To.be.Done")) {
            String etiquetaId = etiquetaDic.get(nombreEtiquetas);
            if (etiquetaId != null && etiquetasActuales.contains(etiquetaId)) {
                etiquetasEliminar.add(etiquetaId);
            }
        }

        List<String> etiquetasAnadir = Collections.singletonList(nuevaEtiquetaId);

        ModifyMessageRequest mods = new ModifyMessageRequest()
                .setRemoveLabelIds(etiquetasEliminar)
                .setAddLabelIds(etiquetasAnadir);

        servicio.users().messages().modify(usuarioId, mensajeId, mods).execute();
        System.out.println("Se ha etiquetado correctamente el mensaje con: " + mensajeEtiqueta);
    }




    static Map<String, String> obtenerMapaEtiquetas(Gmail servicio) throws IOException {
        Map<String, String> nombreDicId = new HashMap<>();
        Map<String, String> IdNombreDic = new HashMap<>();

        List<Label> etiquetas = servicio.users().labels().list("me").execute().getLabels();

        for (Label Etiqueta : etiquetas) {
            nombreDicId.put(Etiqueta.getName(), Etiqueta.getId());
            IdNombreDic.put(Etiqueta.getId(), Etiqueta.getName());
        }

        for (String etiquetaNombre : Arrays.asList("Done", "Work.in.Progress", "To.be.Done")) {
            if (!nombreDicId.containsKey(etiquetaNombre)) {
                System.out.println("La etiqueta '" + etiquetaNombre + "' no existe. Creándola...");
                String etiquetaId = crearEtiquetas(servicio, etiquetaNombre);
                nombreDicId.put(etiquetaNombre, etiquetaId);
                IdNombreDic.put(etiquetaId, etiquetaNombre);
            }
        }

        nombreDicId.putAll(IdNombreDic);

        return nombreDicId;
    }

    private static String crearEtiquetas(Gmail servicio, String nombreEtiqueta) throws IOException {
        Label nuevaEtiqueta = new Label()
                .setName(nombreEtiqueta)
                .setLabelListVisibility("labelShow")
                .setMessageListVisibility("show");

        Label crearEtiqueta = servicio.users().labels().create("me", nuevaEtiqueta).execute();
        System.out.println("Etiqueta creada: " + nombreEtiqueta);
        return crearEtiqueta.getId();
    }

}
