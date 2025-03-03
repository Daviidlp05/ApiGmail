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
    private static final String APPLICATION_NAME = "Gmail Labeler App";
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
    private static final String TOKENS_DIRECTORY_PATH = "tokens";
    private static final List<String> SCOPES = Collections.singletonList("https://www.googleapis.com/auth/gmail.modify");
    private static final String CREDENTIALS_FILE_PATH = "src/main/resources/credentials.json";

    private static Gmail service;

    public static void main(String[] args) throws IOException, GeneralSecurityException {

        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        service = new Gmail.Builder(HTTP_TRANSPORT, JSON_FACTORY, conseguiCredenciales(HTTP_TRANSPORT))
                .setApplicationName(APPLICATION_NAME)
                .build();

        List<Message> messages = conseguirMensajes(service, "me");
        if (messages.isEmpty()) {
            System.out.println("No hay correos en la bandeja de entrada.");
            return;
        }

        SwingUtilities.invokeLater(() -> new EmailVista(service, messages));
    }
    private static Credential conseguiCredenciales(final NetHttpTransport HTTP_TRANSPORT) throws IOException {
        File credentialsFile = new File(CREDENTIALS_FILE_PATH);
        if (!credentialsFile.exists()) {
            throw new FileNotFoundException("Archivo de credenciales no encontrado: " + credentialsFile.getAbsolutePath());
        }

        InputStream in = new FileInputStream(credentialsFile);
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(new File(TOKENS_DIRECTORY_PATH)))
                .setAccessType("offline")
                .build();
        return new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("user");
    }

    static List<Message> conseguirMensajes(Gmail service, String userId) throws IOException {
        ListMessagesResponse response = service.users().messages().list(userId)
                .setQ("in:all")
                .setMaxResults(7L)
                .execute();

        List<Message> messages = response.getMessages();

        if (messages == null || messages.isEmpty()) {
            return new ArrayList<>();
        }

        List<Message> validMessages = new ArrayList<>();
        for (Message message : messages) {
            Message fullMessage = service.users().messages().get(userId, message.getId()).execute();
            if (!fullMessage.getSnippet().contains("Address not found") && !fullMessage.getSnippet().contains("550 5.1.1")) {
                validMessages.add(fullMessage);
            }
        }

        return validMessages;
    }

    public static List<Message> etiquetarCorreos(Gmail service, List<Message> messages) throws IOException {
        if (messages.size() < 7) {
            System.out.println("No hay suficientes mensajes para etiquetar.");
            return messages;
        }

        Map<String, String> labelMap = obtenerMapaEtiquetas(service);
        System.out.println("Iniciando etiquetado de correos...");

        int doneCount = 3, inProgressCount = 1;
        for (int i = 0; i < messages.size(); i++) {
            Message message = service.users().messages().get("me", messages.get(i).getId()).execute();
            List<String> labelIds = message.getLabelIds();

            if (labelIds.contains(labelMap.get("Done")) || labelIds.contains(labelMap.get("Work.in.Progress")) || labelIds.contains(labelMap.get("To.be.Done"))) {
                System.out.println("El mensaje " + message.getId() + " ya tiene una etiqueta. Saltando...");
                continue;
            }

            String label = (i < doneCount) ? "Done" : (i < doneCount + inProgressCount) ? "Work.in.Progress" : "To.be.Done";
            System.out.println("Asignando etiqueta '" + label + "' a mensaje ID: " + message.getId());

            modificarMensaje(service, "me", message.getId(), label);
        }

        System.out.println("Proceso de etiquetado finalizado.");

        return conseguirMensajes(service, "me");
    }


    private static void modificarMensaje(Gmail service, String userId, String messageId, String newLabelName) throws IOException {
        Map<String, String> labelMap = obtenerMapaEtiquetas(service);
        String newLabelId = labelMap.get(newLabelName);

        if (newLabelId == null) {
            System.out.println("La etiqueta '" + newLabelName + "' no se encontró en Gmail. Verifica que existe.");
            return;
        }

        Message message = service.users().messages().get(userId, messageId).execute();
        List<String> existingLabels = message.getLabelIds();

        if (existingLabels.contains(newLabelId)) {
            System.out.println("El mensaje ya tiene la etiqueta '" + newLabelName + "'. Saltando...");
            return;
        }

        List<String> labelsToRemove = new ArrayList<>();
        for (String labelName : Arrays.asList("Done", "Work.in.Progress", "To.be.Done")) {
            String labelId = labelMap.get(labelName);
            if (labelId != null && existingLabels.contains(labelId)) {
                labelsToRemove.add(labelId);
            }
        }

        List<String> labelsToAdd = Collections.singletonList(newLabelId);

        ModifyMessageRequest mods = new ModifyMessageRequest()
                .setRemoveLabelIds(labelsToRemove)
                .setAddLabelIds(labelsToAdd);

        service.users().messages().modify(userId, messageId, mods).execute();
        System.out.println("Se ha etiquetado correctamente el mensaje con: " + newLabelName);
    }




    static Map<String, String> obtenerMapaEtiquetas(Gmail service) throws IOException {
        Map<String, String> nameToIdMap = new HashMap<>();
        Map<String, String> idToNameMap = new HashMap<>();

        List<Label> labels = service.users().labels().list("me").execute().getLabels();

        for (Label label : labels) {
            nameToIdMap.put(label.getName(), label.getId());
            idToNameMap.put(label.getId(), label.getName());
        }

        for (String labelName : Arrays.asList("Done", "Work.in.Progress", "To.be.Done")) {
            if (!nameToIdMap.containsKey(labelName)) {
                System.out.println("La etiqueta '" + labelName + "' no existe. Creándola...");
                String labelId = crearEtiquetas(service, labelName);
                nameToIdMap.put(labelName, labelId);
                idToNameMap.put(labelId, labelName);
            }
        }

        nameToIdMap.putAll(idToNameMap);

        return nameToIdMap;
    }




    private static String crearEtiquetas(Gmail service, String labelName) throws IOException {
        Label newLabel = new Label()
                .setName(labelName)
                .setLabelListVisibility("labelShow")
                .setMessageListVisibility("show");

        Label createdLabel = service.users().labels().create("me", newLabel).execute();
        System.out.println("Etiqueta creada: " + labelName);
        return createdLabel.getId();
    }

}
