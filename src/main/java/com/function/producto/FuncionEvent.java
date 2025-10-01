package com.function.producto;

import java.util.logging.Logger;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.EventGridTrigger;
import com.microsoft.azure.functions.annotation.FunctionName;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

public class FuncionEvent {

    @FunctionName("Event")
    public void run(
            @EventGridTrigger(name = "eventGridEvent") String content,
            final ExecutionContext context) {
        Logger logger = context.getLogger();
        logger.info("Function con Event Grid trigger ejecutada.");

        Gson gson = new Gson();
        JsonObject eventGridEvent = gson.fromJson(content, JsonObject.class);

        logger.info("Evento recibido: " + eventGridEvent.toString());

        String eventtype = eventGridEvent.get("eventType").getAsString();
        String data = eventGridEvent.get("data").getAsString();

        logger.info("Tipo de evento: " + eventtype);
        logger.info("Data del Evento: " + data);

    }
}
