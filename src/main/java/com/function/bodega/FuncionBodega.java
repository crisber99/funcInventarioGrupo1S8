package com.function.bodega;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.function.bodega.Bodega;
import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

public class FuncionBodega {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @FunctionName("Bodegas")
    public HttpResponseMessage handleBaseRequests(
            @HttpTrigger(name = "req", methods = { HttpMethod.GET,
                    HttpMethod.POST }, route = "bodegas", authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {

        String dbConnectionString = System.getenv("DB_CONNECTION_STRING");
        String dbUser = System.getenv("DB_USER");
        String dbPassword = System.getenv("DB_PASSWORD");

        Properties connectionProps = new Properties();
        connectionProps.put("user", dbUser);
        connectionProps.put("password", dbPassword);

        try (Connection conn = DriverManager.getConnection(dbConnectionString, connectionProps)) {

            if (request.getHttpMethod() == HttpMethod.GET) {
                // READ (Obtener todos los bodegas)
                return readAllBodegas(conn, request);
            } else if (request.getHttpMethod() == HttpMethod.POST) {
                // CREATE
                Bodega newBodega = objectMapper.readValue(request.getBody().get(), Bodega.class);
                return createBodega(conn, newBodega, request);
            }

        } catch (Exception e) {
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("Content-Type", "application/json").body("Error: " + e.getMessage())
                    .build();
        }
        return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                .header("Content-Type", "application/json")
                .body("Método no soportado en esta ruta.").build();
    }

    @FunctionName("BodegasById")
    public HttpResponseMessage handleIdRequests(
            @HttpTrigger(name = "req", methods = { HttpMethod.GET, HttpMethod.PUT,
                    HttpMethod.DELETE }, route = "bodegas/{id}", authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
            @BindingName("id") String id,
            final ExecutionContext context) {

        String dbConnectionString = System.getenv("DB_CONNECTION_STRING");
        String dbUser = System.getenv("DB_USER");
        String dbPassword = System.getenv("DB_PASSWORD");

        Properties connectionProps = new Properties();
        connectionProps.put("user", dbUser);
        connectionProps.put("password", dbPassword);

        try (Connection conn = DriverManager.getConnection(dbConnectionString, connectionProps)) {

            if (request.getHttpMethod() == HttpMethod.GET) {
                // READ (Obtener un bodega por ID)
                return readBodegaById(conn, Integer.parseInt(id), request);
            } else if (request.getHttpMethod() == HttpMethod.PUT) {
                // UPDATE
                Bodega updatedBodega = objectMapper.readValue(request.getBody().get(), Bodega.class);
                return updateBodega(conn, Integer.parseInt(id), updatedBodega, request);
            } else if (request.getHttpMethod() == HttpMethod.DELETE) {
                // DELETE
                return deleteBodega(conn, Integer.parseInt(id), request);
            }

        } catch (Exception e) {
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("Content-Type", "application/json")
                    .body("Error: " + e.getMessage())
                    .build();
        }
        return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                .header("Content-Type", "application/json")
                .body("Método no soportado en esta ruta.").build();
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

    private HttpResponseMessage createBodega(Connection conn, Bodega newBodega,
            HttpRequestMessage<Optional<String>> request) throws SQLException {
        String query = "INSERT INTO BODEGAS (NOMBRE, DIRECCION, CAPACIDAD) VALUES (?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, newBodega.getNombre());
            stmt.setString(2, newBodega.getDireccion());
            stmt.setDouble(3, newBodega.getCapacidad());
            stmt.executeUpdate();
            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    newBodega.setId(generatedKeys.getInt(1));
                    return request.createResponseBuilder(HttpStatus.CREATED)
                            .header("Content-Type", "application/json")
                            .body(newBodega).build();
                }
            }
        }
        return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                .header("Content-Type", "application/json")
                .body("Error al crear la bodega.")
                .build();
    }

    private HttpResponseMessage updateBodega(Connection conn, int id, Bodega updatedBodega,
            HttpRequestMessage<Optional<String>> request) throws SQLException {
        String query = "UPDATE BODEGAS SET NOMBRE=?, DIRECCION=?, CAPACIDAD=? WHERE ID=?";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, updatedBodega.getNombre());
            stmt.setString(2, updatedBodega.getDireccion());
            stmt.setDouble(3, updatedBodega.getCapacidad());
            stmt.setInt(4, id);
            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected > 0) {
                return request.createResponseBuilder(HttpStatus.OK)
                        .header("Content-Type", "application/json")
                        .body(updatedBodega).build();
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
                        .header("Content-Type", "application/json")
                        .body("Bodega no encontrada.").build();
            }
        }
    }
}