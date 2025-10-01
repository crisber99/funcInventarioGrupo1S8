package com.function.bodega;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;

public class GraphQLBodega {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @FunctionName("GraphQLBodegas")
    public HttpResponseMessage run(
            @HttpTrigger(name = "req", methods = {
                    HttpMethod.POST }, route = "graphqlBodega", authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {

        String dbConnectionString = System.getenv("DB_CONNECTION_STRING");
        String dbUser = System.getenv("DB_USER");
        String dbPassword = System.getenv("DB_PASSWORD");

        Properties connectionProps = new Properties();
        connectionProps.put("user", dbUser);
        connectionProps.put("password", dbPassword);

        try (Connection conn = DriverManager.getConnection(dbConnectionString, connectionProps)) {

            Map<String, Object> requestBody = objectMapper.readValue(
                    request.getBody().get(),
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                    });

            String query = (String) requestBody.get("query");
            Map<String, Object> variables = (Map<String, Object>) requestBody.get("variables");

            if (query != null && query.contains("mutation")) {
                if (query.contains("createBodega")) {
                    if (variables != null && variables.containsKey("bodega")) {
                        Map<String, Object> bodegaData = (Map<String, Object>) variables.get("bodega");
                        return createBodega(conn, bodegaData, request);
                    } else {
                        throw new IllegalArgumentException("El objeto 'bodega' no fue proporcionado en las variables.");
                    }
                } else if (query.contains("updateBodega")) {
                    if (variables != null && variables.containsKey("id") && variables.containsKey("bodega")) {
                        int id = ((Number) variables.get("id")).intValue();
                        Map<String, Object> bodegaData = (Map<String, Object>) variables.get("bodega");
                        return updateBodega(conn, id, bodegaData, request);
                    } else {
                        throw new IllegalArgumentException("El ID o el objeto 'bodega' no fue proporcionado.");
                    }
                } else if (query.contains("deleteBodega")) {
                    int id = ((Number) variables.get("id")).intValue();
                    return deleteBodega(conn, id, request);
                }
            } else if (query != null && query.contains("query")) {
                if (variables != null && variables.containsKey("id")) {
                    int id = ((Number) variables.get("id")).intValue();
                    return readBodegaById(conn, id, request);
                } else {
                    return readAllBodegas(conn, request);
                }
            }
        } catch (Exception e) {
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("Content-Type", "application/json")
                    .body("Error en la operación GraphQL: " + e.getMessage()).build();
        }
        return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                .header("Content-Type", "application/json")
                .body("Operación GraphQL no soportada.").build();
    }

    private HttpResponseMessage readAllBodegas(Connection conn, HttpRequestMessage<Optional<String>> request)
            throws SQLException {
        List<Bodega> bodegas = new ArrayList<>();
        String query = "SELECT ID, NOMBRE, DIRECCION, CAPACIDAD FROM BODEGAS";
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(query)) {
            while (rs.next()) {
                Bodega bodega = new Bodega();
                bodega.setId(rs.getInt("ID"));
                bodega.setNombre(rs.getString("NOMBRE"));
                bodega.setDireccion(rs.getString("DIRECCION"));
                bodega.setCapacidad(rs.getInt("CAPACIDAD"));
                bodegas.add(bodega);
            }
        }
        return request.createResponseBuilder(HttpStatus.OK)
                .header("Content-Type", "application/json")
                .body(bodegas).build();
    }

    private HttpResponseMessage readBodegaById(Connection conn, int id, HttpRequestMessage<Optional<String>> request)
            throws SQLException {
        String query = "SELECT ID, NOMBRE, DIRECCION, CAPACIDAD FROM BODEGAS WHERE ID = ?";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                Bodega bodega = new Bodega();
                bodega.setId(rs.getInt("ID"));
                bodega.setNombre(rs.getString("NOMBRE"));
                bodega.setDireccion(rs.getString("DIRECCION"));
                bodega.setCapacidad(rs.getInt("CAPACIDAD"));
                return request.createResponseBuilder(HttpStatus.OK)
                        .header("Content-Type", "application/json")
                        .body(bodega).build();
            } else {
                return request.createResponseBuilder(HttpStatus.NOT_FOUND)
                        .header("Content-Type", "application/json")
                        .body("Bodega no encontrada.").build();
            }
        }
    }

    private HttpResponseMessage createBodega(Connection conn, Map<String, Object> bodegaData,
            HttpRequestMessage<Optional<String>> request) throws SQLException {
        String query = "INSERT INTO BODEGAS (NOMBRE, DIRECCION, CAPACIDAD) VALUES (?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, (String) bodegaData.get("nombre"));
            stmt.setString(2, (String) bodegaData.get("direccion"));
            stmt.setInt(3, ((Number) bodegaData.get("capacidad")).intValue());
            stmt.executeUpdate();
            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    Bodega newBodega = new Bodega();
                    newBodega.setId(generatedKeys.getInt(1));
                    newBodega.setNombre((String) bodegaData.get("nombre"));
                    newBodega.setDireccion((String) bodegaData.get("direccion"));
                    newBodega.setCapacidad(((Number) bodegaData.get("capacidad")).intValue());
                    return request.createResponseBuilder(HttpStatus.CREATED)
                            .header("Content-Type", "application/json")
                            .body(newBodega).build();
                }
            }
        }
        return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                .header("Content-Type", "application/json")
                .body("Error al crear bodega.").build();
    }

    private HttpResponseMessage updateBodega(Connection conn, int id, Map<String, Object> bodegaData,
            HttpRequestMessage<Optional<String>> request) throws SQLException {
        String query = "UPDATE BODEGAS SET NOMBRE=?, DIRECCION=?, CAPACIDAD=? WHERE ID=?";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, (String) bodegaData.get("nombre"));
            stmt.setString(2, (String) bodegaData.get("direccion"));
            stmt.setInt(3, ((Number) bodegaData.get("capacidad")).intValue());
            stmt.setInt(4, id);
            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected > 0) {
                return request.createResponseBuilder(HttpStatus.OK)
                        .header("Content-Type", "application/json")
                        .body(bodegaData).build();
            } else {
                return request.createResponseBuilder(HttpStatus.NOT_FOUND)
                        .header("Content-Type", "application/json")
                        .body("Bodega no encontrada.").build();
            }
        }
    }

    private HttpResponseMessage deleteBodega(Connection conn, int id, HttpRequestMessage<Optional<String>> request)
            throws SQLException {
        String query = "DELETE FROM BODEGAS WHERE ID = ?";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, id);
            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected > 0) {
                return request.createResponseBuilder(HttpStatus.NO_CONTENT)
                        .header("Content-Type", "application/json")
                        .build();
            } else {
                return request.createResponseBuilder(HttpStatus.NOT_FOUND)
                        .header("Content-Type", "application/json").body("Bodega no encontrada.").build();
            }
        }
    }
}
